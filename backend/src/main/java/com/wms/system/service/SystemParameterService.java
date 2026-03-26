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

    private final SystemParameterRepository systemParameterRepository;

    public List<SystemParameter> findAll() {
        return systemParameterRepository.findAll();
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
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (key=" + paramKey + ")");
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
            throw new OptimisticLockConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "他のユーザーによる更新が先行しました (key=" + paramKey + ")");
        }
    }

    private void validateParamValue(SystemParameter param, String newValue) {
        if ("INTEGER".equals(param.getValueType())) {
            try {
                Integer.parseInt(newValue);
            } catch (NumberFormatException e) {
                throw new BusinessRuleViolationException(
                        "INVALID_PARAM_VALUE",
                        "INTEGER型パラメータに不正な値: " + newValue);
            }
        } else if ("BOOLEAN".equals(param.getValueType())) {
            if (!"true".equalsIgnoreCase(newValue) && !"false".equalsIgnoreCase(newValue)) {
                throw new BusinessRuleViolationException(
                        "INVALID_PARAM_VALUE",
                        "BOOLEAN型パラメータに不正な値: " + newValue);
            }
        }
    }
}
