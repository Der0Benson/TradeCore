package de.tradecore.legacy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Calculator {
    private static final Pattern EXPRESSION = Pattern.compile("^\\s*([0-9]+\\.?[0-9]*)\\s*([+\\-*/xX])\\s*([0-9]+\\.?[0-9]*)\\s*$");
    private Calculator() {}

    public static boolean isExpression(String input) {
        return input != null && EXPRESSION.matcher(input).matches();
    }

    public static String evaluate(String input) {
        if (input == null) return null;
        Matcher matcher = EXPRESSION.matcher(input);
        if (!matcher.matches()) return null;
        try {
            BigDecimal left = new BigDecimal(matcher.group(1));
            BigDecimal right = new BigDecimal(matcher.group(3));
            BigDecimal value;
            String operator = matcher.group(2);
            if ("+".equals(operator)) value = left.add(right);
            else if ("-".equals(operator)) value = left.subtract(right);
            else if ("*".equals(operator) || "x".equalsIgnoreCase(operator)) value = left.multiply(right);
            else if (right.compareTo(BigDecimal.ZERO) == 0) return "Fehler: Division durch null";
            else value = left.divide(right, 10, RoundingMode.HALF_UP);
            return "= " + value.stripTrailingZeros().toPlainString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
