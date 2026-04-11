package com.wms.system.service;

import com.wms.shared.exception.BusinessRuleViolationException;
import com.wms.shared.exception.OptimisticLockConflictException;
import com.wms.shared.exception.ResourceNotFoundException;
import com.wms.system.entity.SystemParameter;
import com.wms.system.repository.SystemParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SystemParameterService {

    private static final int STRING_MAX_LENGTH = 500;

    private final SystemParameterRepository systemParameterRepository;

    public List<SystemParameter> findAll() {
        return systemParameterRepository.findAllByOrderByCategoryAscDisplayOrderAsc();
    }

    public SystemParameter findByKey(String paramKey) {
        return systemParameterRepository.findByParamKey(paramKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SYSTEM_PARAMETER_NOT_FOUND",
                        "システムパラメータが見つかりません: " + paramKey));
    }

    public int getIntValue(String paramKey) {
        return findByKey(paramKey).getIntValue();
    }

    @Transactional
    public SystemParameter updateValue(String paramKey, String paramValue, Integer version) {
        SystemParameter param = findByKey(paramKey);
        if (!param.getVersion().equals(version)) {
            log.info("SystemParameter optimistic lock conflict: key={}", paramKey);
            throw OptimisticLockConflictException.standard();
        }
        validateParamValue(param, paramValue);
        // BOOLEAN型は小文字に正規化（大文字小文字の揺れを防止）
        String normalizedValue = "BOOLEAN".equals(param.getValueType())
                ? paramValue.toLowerCase() : paramValue;
        param.setParamValue(normalizedValue);
        param.setVersion(version);
        try {
            SystemParameter saved = systemParameterRepository.save(param);
            log.info("SystemParameter updated: key={}", paramKey);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("SystemParameter optimistic lock conflict: key={}", paramKey);
            throw OptimisticLockConflictException.standard();
        }
    }

    private void validateParamValue(SystemParameter param, String newValue) {
        if (newValue == null) {
            throw new BusinessRuleViolationException(
                    "INVALID_PARAM_VALUE",
                    "パラメータ値にnullは設定できません");
        }
        if ("INTEGER".equals(param.getValueType())) {
            // 設計書: ^[1-9][0-9]*$ または 0（先頭ゼロ禁止、負値禁止）
            if (!newValue.matches("^(0|[1-9][0-9]*)$")) {
                log.warn("INVALID_PARAM_VALUE: INTEGER type invalid value rejected: key={}, value={}",
                        param.getParamKey(), sanitize(newValue));
                throw new BusinessRuleViolationException(
                        "INVALID_PARAM_VALUE",
                        "INTEGER型パラメータに不正な値が指定されました");
            }
        } else if ("STRING".equals(param.getValueType())) {
            if (newValue.isBlank()) {
                log.warn("INVALID_PARAM_VALUE: STRING type blank value rejected: key={}", param.getParamKey());
                throw new BusinessRuleViolationException(
                        "INVALID_PARAM_VALUE",
                        "STRING型パラメータに空の値は設定できません");
            }
            if (newValue.length() > STRING_MAX_LENGTH) {
                log.warn("INVALID_PARAM_VALUE: STRING type value too long rejected: key={}, length={}",
                        param.getParamKey(), newValue.length());
                throw new BusinessRuleViolationException(
                        "INVALID_PARAM_VALUE",
                        "STRING型パラメータは" + STRING_MAX_LENGTH + "文字以内で入力してください");
            }
        } else if ("BOOLEAN".equals(param.getValueType())) {
            if (!"true".equalsIgnoreCase(newValue) && !"false".equalsIgnoreCase(newValue)) {
                log.warn("INVALID_PARAM_VALUE: BOOLEAN type invalid value rejected: key={}, value={}",
                        param.getParamKey(), sanitize(newValue));
                throw new BusinessRuleViolationException(
                        "INVALID_PARAM_VALUE",
                        "BOOLEAN型パラメータに不正な値が指定されました");
            }
        }
    }

    /** ログインジェクション防止のため改行・タブ文字を除去する */
    private String sanitize(String value) {
        return value.replaceAll("[\r\n\t]", "_");
    }
}
