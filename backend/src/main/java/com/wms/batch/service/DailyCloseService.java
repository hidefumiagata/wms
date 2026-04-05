package com.wms.batch.service;

import com.wms.report.entity.BatchExecutionLog;
import com.wms.report.repository.BatchExecutionLogRepository;
import com.wms.shared.exception.DuplicateResourceException;
import com.wms.shared.security.WmsUserDetails;
import com.wms.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyCloseService {

    private static final String[] STEP_NAMES = {
            "営業日更新",
            "入荷実績集計",
            "出荷実績集計",
            "在庫集計",
            "アーカイブ+リスト生成",
            "日次集計レコード生成"
    };

    private final BatchExecutionLogRepository logRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager txManager;
    private final UserService userService;

    public record StepResult(String status, String errorMessage) {
        public static StepResult success() { return new StepResult("SUCCESS", null); }
        public static StepResult failed(String msg) { return new StepResult("FAILED", msg); }
        public static StepResult skipped() { return new StepResult("SUCCESS", null); }
        public boolean isFailed() { return "FAILED".equals(status); }
    }

    public record DailyCloseResult(
            Long executionId,
            String status,
            LocalDate targetBusinessDate,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            List<StepResultDetail> steps,
            Integer unreceivedCount,
            Integer unshippedCount
    ) {}

    public record StepResultDetail(int step, String name, String status, String errorMessage) {}

    public DailyCloseResult execute(LocalDate targetBusinessDate) {
        WmsUserDetails userDetails = (WmsUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        Long executedBy = userDetails.getUserId();

        // Check for SUCCESS record (duplicate execution prevention)
        if (logRepository.existsByTargetBusinessDateAndStatus(targetBusinessDate, "SUCCESS")) {
            throw new DuplicateResourceException("BATCH_ALREADY_RUNNING",
                    targetBusinessDate + " は既に処理済みです。同一営業日への二重実行はできません。");
        }
        if (logRepository.existsByTargetBusinessDateAndStatus(targetBusinessDate, "RUNNING")) {
            throw new DuplicateResourceException("BATCH_ALREADY_RUNNING",
                    targetBusinessDate + " のバッチ処理が実行中です。");
        }

        // Check for FAILED record (retry scenario)
        boolean[] previouslyCompleted = new boolean[6];
        Optional<BatchExecutionLog> failedLog = logRepository.findByTargetBusinessDateAndStatus(
                targetBusinessDate, "FAILED");
        if (failedLog.isPresent()) {
            for (int i = 0; i < 6; i++) {
                previouslyCompleted[i] = "SUCCESS".equals(failedLog.get().getStepStatus(i + 1));
            }
            // Delete FAILED record in a new transaction
            executeInNewTransaction(() -> {
                logRepository.deleteFailedByTargetBusinessDate(targetBusinessDate);
            });
        }

        // Create RUNNING record
        OffsetDateTime startedAt = OffsetDateTime.now();
        BatchExecutionLog execLog = executeInNewTransaction(() -> {
            BatchExecutionLog newLog = BatchExecutionLog.builder()
                    .targetBusinessDate(targetBusinessDate)
                    .status("RUNNING")
                    .startedAt(startedAt)
                    .executedBy(executedBy)
                    .build();
            return logRepository.save(newLog);
        });

        // Execute each step
        StepResult[] results = new StepResult[6];
        for (int step = 1; step <= 6; step++) {
            if (previouslyCompleted[step - 1]) {
                results[step - 1] = StepResult.skipped();
                final int s = step;
                executeInNewTransaction(() -> {
                    updateStepStatus(execLog.getId(), s, "SUCCESS");
                });
                continue;
            }

            final int currentStep = step;
            try {
                results[step - 1] = executeStepInNewTransaction(currentStep, targetBusinessDate, execLog.getId());
            } catch (Exception e) {
                results[step - 1] = StepResult.failed(e.getMessage());
            }

            final StepResult result = results[step - 1];
            executeInNewTransaction(() -> {
                updateStepStatus(execLog.getId(), currentStep, result.status());
            });

            if (result.isFailed()) {
                // Mark remaining steps as SKIPPED
                for (int remaining = step + 1; remaining <= 6; remaining++) {
                    results[remaining - 1] = StepResult.skipped();
                    final int r = remaining;
                    executeInNewTransaction(() -> {
                        updateStepStatus(execLog.getId(), r, "SKIPPED");
                    });
                }
                // Mark execution as FAILED
                OffsetDateTime completedAt = OffsetDateTime.now();
                String errorMsg = truncateError(result.errorMessage());
                executeInNewTransaction(() -> {
                    logRepository.updateStatusWithError(execLog.getId(), "FAILED", errorMsg, completedAt);
                });

                return buildResult(execLog.getId(), "FAILED", targetBusinessDate,
                        startedAt, completedAt, results);
            }
        }

        // All steps succeeded
        OffsetDateTime completedAt = OffsetDateTime.now();
        executeInNewTransaction(() -> {
            logRepository.updateStatus(execLog.getId(), "SUCCESS", completedAt);
        });

        return buildResult(execLog.getId(), "SUCCESS", targetBusinessDate,
                startedAt, completedAt, results);
    }

    public String resolveUserName(Long userId) {
        return userService.getUserFullName(userId);
    }

    private StepResult executeStepInNewTransaction(int step, LocalDate targetDate, Long logId) {
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        return txTemplate.execute(status -> {
            return switch (step) {
                case 1 -> step1UpdateBusinessDate(targetDate);
                case 2 -> step2AggregateInbound(targetDate);
                case 3 -> step3AggregateOutbound(targetDate);
                case 4 -> step4SnapshotInventory(targetDate);
                case 5 -> step5ArchiveAndGenerateLists(targetDate, logId);
                case 6 -> step6GenerateDailySummary(targetDate);
                default -> throw new IllegalArgumentException("Unknown step: " + step);
            };
        });
    }

    // Step 1: Update business date
    private StepResult step1UpdateBusinessDate(LocalDate targetDate) {
        LocalDate currentDate = jdbcTemplate.queryForObject(
                "SELECT current_business_date FROM business_date WHERE id = 1 FOR UPDATE",
                LocalDate.class);

        if (targetDate.equals(currentDate)) {
            // Already updated (idempotent)
            return StepResult.success();
        }

        if (!currentDate.plusDays(1).equals(targetDate)) {
            return StepResult.failed("営業日が連続していません。現在営業日: " + currentDate
                    + ", 対象営業日: " + targetDate + "。連続する翌日のみ指定可能です。");
        }

        jdbcTemplate.update(
                "UPDATE business_date SET current_business_date = ?, updated_at = now() WHERE id = 1",
                targetDate);

        log.info("Step 1: Business date updated to {}", targetDate);
        return StepResult.success();
    }

    // Step 2: Aggregate inbound
    private StepResult step2AggregateInbound(LocalDate targetDate) {
        jdbcTemplate.update("""
                INSERT INTO inbound_summaries (
                    business_date, warehouse_id, warehouse_code,
                    inbound_count, inbound_line_count, inbound_quantity_total, created_at
                )
                SELECT
                    ?, s.warehouse_id, w.warehouse_code,
                    COUNT(DISTINCT s.id),
                    COUNT(l.id),
                    COALESCE(SUM(l.inspected_qty * CASE
                        WHEN l.unit_type = 'CASE' THEN p.case_quantity * p.ball_quantity
                        WHEN l.unit_type = 'BALL' THEN p.ball_quantity
                        ELSE 1
                    END), 0),
                    NOW()
                FROM inbound_slips s
                JOIN inbound_slip_lines l ON l.inbound_slip_id = s.id
                JOIN warehouses w ON w.id = s.warehouse_id
                JOIN products p ON p.id = l.product_id
                WHERE s.status = 'STORED'
                  AND l.stored_at::date = ?
                GROUP BY s.warehouse_id, w.warehouse_code
                ON CONFLICT (business_date, warehouse_id)
                DO UPDATE SET
                    inbound_count = EXCLUDED.inbound_count,
                    inbound_line_count = EXCLUDED.inbound_line_count,
                    inbound_quantity_total = EXCLUDED.inbound_quantity_total
                """, targetDate, targetDate);

        log.info("Step 2: Inbound aggregation completed for {}", targetDate);
        return StepResult.success();
    }

    // Step 3: Aggregate outbound
    private StepResult step3AggregateOutbound(LocalDate targetDate) {
        jdbcTemplate.update("""
                INSERT INTO outbound_summaries (
                    business_date, warehouse_id, warehouse_code,
                    outbound_count, outbound_line_count, outbound_quantity_total, created_at
                )
                SELECT
                    ?, s.warehouse_id, w.warehouse_code,
                    COUNT(DISTINCT s.id),
                    COUNT(l.id),
                    COALESCE(SUM(l.shipped_qty * CASE
                        WHEN l.unit_type = 'CASE' THEN p.case_quantity * p.ball_quantity
                        WHEN l.unit_type = 'BALL' THEN p.ball_quantity
                        ELSE 1
                    END), 0),
                    NOW()
                FROM outbound_slips s
                JOIN outbound_slip_lines l ON l.outbound_slip_id = s.id
                JOIN warehouses w ON w.id = s.warehouse_id
                JOIN products p ON p.id = l.product_id
                WHERE s.status = 'SHIPPED'
                  AND s.shipped_at::date = ?
                GROUP BY s.warehouse_id, w.warehouse_code
                ON CONFLICT (business_date, warehouse_id)
                DO UPDATE SET
                    outbound_count = EXCLUDED.outbound_count,
                    outbound_line_count = EXCLUDED.outbound_line_count,
                    outbound_quantity_total = EXCLUDED.outbound_quantity_total
                """, targetDate, targetDate);

        log.info("Step 3: Outbound aggregation completed for {}", targetDate);
        return StepResult.success();
    }

    // Step 4: Snapshot inventory
    private StepResult step4SnapshotInventory(LocalDate targetDate) {
        jdbcTemplate.update("""
                INSERT INTO inventory_snapshots (
                    business_date, warehouse_id, warehouse_code,
                    product_id, product_code, product_name, unit_type,
                    total_quantity, created_at
                )
                SELECT
                    ?, loc.warehouse_id, w.warehouse_code,
                    i.product_id, p.product_code, p.product_name, i.unit_type,
                    SUM(i.quantity), NOW()
                FROM inventories i
                JOIN locations loc ON loc.id = i.location_id
                JOIN warehouses w ON w.id = loc.warehouse_id
                JOIN products p ON p.id = i.product_id
                WHERE i.quantity > 0
                GROUP BY loc.warehouse_id, w.warehouse_code,
                         i.product_id, p.product_code, p.product_name, i.unit_type
                ON CONFLICT (business_date, warehouse_id, product_id, unit_type)
                DO UPDATE SET
                    total_quantity = EXCLUDED.total_quantity,
                    product_name = EXCLUDED.product_name
                """, targetDate);

        log.info("Step 4: Inventory snapshot completed for {}", targetDate);
        return StepResult.success();
    }

    // Step 5: Archive + Generate unreceived/unshipped lists
    private StepResult step5ArchiveAndGenerateLists(LocalDate targetDate, Long logId) {
        // 5a: Backup old data (2 months+)
        String twoMonthsAgo = targetDate.minusMonths(2).toString();

        jdbcTemplate.update("""
                INSERT INTO inbound_slips_backup
                SELECT s.*, NOW(), ?
                FROM inbound_slips s
                WHERE s.status = 'STORED'
                  AND s.updated_at < ?::timestamp
                ON CONFLICT DO NOTHING
                """, logId, twoMonthsAgo);

        jdbcTemplate.update("""
                INSERT INTO inbound_slip_lines_backup
                SELECT l.*, NOW(), ?
                FROM inbound_slip_lines l
                WHERE l.inbound_slip_id IN (
                    SELECT id FROM inbound_slips
                    WHERE status = 'STORED'
                      AND updated_at < ?::timestamp
                )
                ON CONFLICT DO NOTHING
                """, logId, twoMonthsAgo);

        jdbcTemplate.update("""
                INSERT INTO outbound_slips_backup
                SELECT s.*, NOW(), ?
                FROM outbound_slips s
                WHERE s.status = 'SHIPPED'
                  AND s.updated_at < ?::timestamp
                ON CONFLICT DO NOTHING
                """, logId, twoMonthsAgo);

        jdbcTemplate.update("""
                INSERT INTO outbound_slip_lines_backup
                SELECT l.*, NOW(), ?
                FROM outbound_slip_lines l
                WHERE l.outbound_slip_id IN (
                    SELECT id FROM outbound_slips
                    WHERE status = 'SHIPPED'
                      AND updated_at < ?::timestamp
                )
                ON CONFLICT DO NOTHING
                """, logId, twoMonthsAgo);

        jdbcTemplate.update("""
                INSERT INTO inventory_movements_backup
                SELECT m.*, NOW(), ?
                FROM inventory_movements m
                WHERE m.executed_at < ?::timestamp
                ON CONFLICT DO NOTHING
                """, logId, twoMonthsAgo);

        // 5b: Generate unreceived list
        jdbcTemplate.update("DELETE FROM unreceived_list_records WHERE batch_business_date = ?", targetDate);
        jdbcTemplate.update("""
                INSERT INTO unreceived_list_records (
                    batch_business_date, inbound_slip_id, slip_number,
                    planned_date, warehouse_code, partner_code, partner_name,
                    product_code, product_name, unit_type, planned_qty,
                    current_status, created_at
                )
                SELECT
                    ?, s.id, s.slip_number, s.planned_date,
                    w.warehouse_code, pr.partner_code, pr.partner_name,
                    p.product_code, p.product_name, l.unit_type, l.planned_qty,
                    s.status, NOW()
                FROM inbound_slips s
                JOIN inbound_slip_lines l ON l.inbound_slip_id = s.id
                JOIN warehouses w ON w.id = s.warehouse_id
                LEFT JOIN partners pr ON pr.id = s.partner_id
                JOIN products p ON p.id = l.product_id
                WHERE s.planned_date <= ?
                  AND s.status NOT IN ('STORED', 'CANCELLED')
                """, targetDate, targetDate);

        // 5c: Generate unshipped list
        jdbcTemplate.update("DELETE FROM unshipped_list_records WHERE batch_business_date = ?", targetDate);
        jdbcTemplate.update("""
                INSERT INTO unshipped_list_records (
                    batch_business_date, outbound_slip_id, slip_number,
                    planned_date, warehouse_code, partner_code, partner_name,
                    product_code, product_name, unit_type, ordered_qty,
                    current_status, created_at
                )
                SELECT
                    ?, s.id, s.slip_number, s.planned_date,
                    w.warehouse_code, pr.partner_code, pr.partner_name,
                    p.product_code, p.product_name, l.unit_type, l.ordered_qty,
                    s.status, NOW()
                FROM outbound_slips s
                JOIN outbound_slip_lines l ON l.outbound_slip_id = s.id
                JOIN warehouses w ON w.id = s.warehouse_id
                LEFT JOIN partners pr ON pr.id = s.partner_id
                JOIN products p ON p.id = l.product_id
                WHERE s.planned_date <= ?
                  AND s.status NOT IN ('SHIPPED', 'CANCELLED')
                """, targetDate, targetDate);

        log.info("Step 5: Archive and list generation completed for {}", targetDate);
        return StepResult.success();
    }

    // Step 6: Generate daily summary records
    private StepResult step6GenerateDailySummary(LocalDate targetDate) {
        jdbcTemplate.update("""
                INSERT INTO daily_summary_records (
                    business_date, warehouse_id, warehouse_code,
                    inbound_count, inbound_line_count, inbound_quantity_total,
                    outbound_count, outbound_line_count, outbound_quantity_total,
                    return_count, return_quantity_total,
                    inventory_quantity_total,
                    unreceived_count, unshipped_count,
                    created_at
                )
                SELECT
                    ?,
                    w.id, w.warehouse_code,
                    COALESCE(ib.inbound_count, 0),
                    COALESCE(ib.inbound_line_count, 0),
                    COALESCE(ib.inbound_quantity_total, 0),
                    COALESCE(ob.outbound_count, 0),
                    COALESCE(ob.outbound_line_count, 0),
                    COALESCE(ob.outbound_quantity_total, 0),
                    COALESCE(rt.return_count, 0),
                    COALESCE(rt.return_quantity_total, 0),
                    COALESCE(inv.inventory_quantity_total, 0),
                    COALESCE(ur.unreceived_count, 0),
                    COALESCE(us.unshipped_count, 0),
                    NOW()
                FROM warehouses w
                LEFT JOIN inbound_summaries ib
                    ON ib.business_date = ? AND ib.warehouse_id = w.id
                LEFT JOIN outbound_summaries ob
                    ON ob.business_date = ? AND ob.warehouse_id = w.id
                LEFT JOIN (
                    SELECT warehouse_id, COUNT(*) AS return_count,
                           SUM(r.quantity * CASE
                               WHEN r.unit_type = 'CASE' THEN p.case_quantity * p.ball_quantity
                               WHEN r.unit_type = 'BALL' THEN p.ball_quantity
                               ELSE 1
                           END) AS return_quantity_total
                    FROM return_slips r
                    JOIN products p ON p.id = r.product_id
                    WHERE r.return_date = ? AND r.status = 'COMPLETED'
                    GROUP BY r.warehouse_id
                ) rt ON rt.warehouse_id = w.id
                LEFT JOIN (
                    SELECT warehouse_id,
                           SUM(sn.total_quantity * CASE
                               WHEN sn.unit_type = 'CASE' THEN p.case_quantity * p.ball_quantity
                               WHEN sn.unit_type = 'BALL' THEN p.ball_quantity
                               ELSE 1
                           END) AS inventory_quantity_total
                    FROM inventory_snapshots sn
                    JOIN products p ON p.id = sn.product_id
                    WHERE sn.business_date = ?
                    GROUP BY sn.warehouse_id
                ) inv ON inv.warehouse_id = w.id
                LEFT JOIN (
                    SELECT warehouse_code, COUNT(DISTINCT inbound_slip_id) AS unreceived_count
                    FROM unreceived_list_records
                    WHERE batch_business_date = ?
                    GROUP BY warehouse_code
                ) ur ON ur.warehouse_code = w.warehouse_code
                LEFT JOIN (
                    SELECT warehouse_code, COUNT(DISTINCT outbound_slip_id) AS unshipped_count
                    FROM unshipped_list_records
                    WHERE batch_business_date = ?
                    GROUP BY warehouse_code
                ) us ON us.warehouse_code = w.warehouse_code
                WHERE w.is_active = true
                ON CONFLICT (business_date, warehouse_id)
                DO UPDATE SET
                    inbound_count = EXCLUDED.inbound_count,
                    inbound_line_count = EXCLUDED.inbound_line_count,
                    inbound_quantity_total = EXCLUDED.inbound_quantity_total,
                    outbound_count = EXCLUDED.outbound_count,
                    outbound_line_count = EXCLUDED.outbound_line_count,
                    outbound_quantity_total = EXCLUDED.outbound_quantity_total,
                    return_count = EXCLUDED.return_count,
                    return_quantity_total = EXCLUDED.return_quantity_total,
                    inventory_quantity_total = EXCLUDED.inventory_quantity_total,
                    unreceived_count = EXCLUDED.unreceived_count,
                    unshipped_count = EXCLUDED.unshipped_count
                """, targetDate, targetDate, targetDate, targetDate, targetDate, targetDate, targetDate);

        log.info("Step 6: Daily summary generation completed for {}", targetDate);
        return StepResult.success();
    }

    private void updateStepStatus(Long logId, int step, String status) {
        String col = "step" + step + "_status";
        jdbcTemplate.update("UPDATE batch_execution_logs SET " + col + " = ? WHERE id = ?", status, logId);
    }

    private <T> T executeInNewTransaction(java.util.function.Supplier<T> action) {
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        return txTemplate.execute(status -> action.get());
    }

    private void executeInNewTransaction(Runnable action) {
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.executeWithoutResult(status -> action.run());
    }

    private DailyCloseResult buildResult(Long executionId, String status,
                                          LocalDate targetDate, OffsetDateTime startedAt,
                                          OffsetDateTime completedAt, StepResult[] results) {
        List<StepResultDetail> steps = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            steps.add(new StepResultDetail(i + 1, STEP_NAMES[i],
                    results[i].status(), results[i].errorMessage()));
        }

        Integer unreceivedCount = null;
        Integer unshippedCount = null;
        if ("SUCCESS".equals(status)) {
            unreceivedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM unreceived_list_records WHERE batch_business_date = ?",
                    Integer.class, targetDate);
            unshippedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM unshipped_list_records WHERE batch_business_date = ?",
                    Integer.class, targetDate);
        }

        return new DailyCloseResult(executionId, status, targetDate, startedAt, completedAt,
                steps, unreceivedCount, unshippedCount);
    }

    private String truncateError(String msg) {
        if (msg == null) return null;
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}
