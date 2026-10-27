package com.wx.community.security;

import com.wx.community.api.BusinessException;

public final class AdminPasswordPolicy {
    private AdminPasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.length() < 9) {
            throw new BusinessException("WEAK_PASSWORD", "密码长度至少为9位");
        }
        if (password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(c -> !Character.isLetterOrDigit(c))) {
            throw new BusinessException("WEAK_PASSWORD", "密码必须包含大写字母、小写字母和符号");
        }
        if (hasSequentialRun(password)) {
            throw new BusinessException("WEAK_PASSWORD", "密码不能包含连续递增或递减的3位字母或数字");
        }
    }

    private static boolean hasSequentialRun(String password) {
        for (int i = 0; i + 2 < password.length(); i++) {
            char first = Character.toLowerCase(password.charAt(i));
            char second = Character.toLowerCase(password.charAt(i + 1));
            char third = Character.toLowerCase(password.charAt(i + 2));
            if (isSequenceCharacter(first) && isSequenceCharacter(second) && isSequenceCharacter(third)
                    && second - first == third - second && Math.abs(second - first) == 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSequenceCharacter(char value) {
        return value >= 'a' && value <= 'z' || value >= '0' && value <= '9';
    }
}
