package Tradecorev2.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class ChatCalculator {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-ChatCalculator");
   private static final Pattern MATH_PATTERN = Pattern.compile("^\\s*([0-9]+\\.?[0-9]*)\\s*([+\\-*/xX])\\s*([0-9]+\\.?[0-9]*)\\s*$");

   public static boolean isMathExpression(String text) {
      return text != null && !text.trim().isEmpty() ? MATH_PATTERN.matcher(text.trim()).matches() : false;
   }

   public static String evaluate(String text) {
      if (text != null && !text.trim().isEmpty()) {
         Matcher matcher = MATH_PATTERN.matcher(text.trim());
         if (!matcher.matches()) {
            return null;
         } else {
            try {
               String leftStr = matcher.group(1);
               String operator = matcher.group(2);
               String rightStr = matcher.group(3);
               BigDecimal left = new BigDecimal(leftStr);
               BigDecimal right = new BigDecimal(rightStr);
               BigDecimal result;
               switch (operator) {
                  case "+":
                     result = left.add(right);
                     break;
                  case "-":
                     result = left.subtract(right);
                     break;
                  case "*":
                  case "x":
                  case "X":
                     result = left.multiply(right);
                     break;
                  case "/":
                     if (right.compareTo(BigDecimal.ZERO) == 0) {
                        return "Error: Division by zero";
                     }

                     result = left.divide(right, 10, RoundingMode.HALF_UP);
                     result = result.stripTrailingZeros();
                     break;
                  default:
                     return null;
               }

               String resultStr = result.toPlainString();
               if (result.abs().compareTo(new BigDecimal("1000")) >= 0) {
                  resultStr = formatWithSeparators(result);
               }

               return "= " + resultStr;
            } catch (ArithmeticException | NumberFormatException e) {
               LOGGER.debug("Failed to evaluate expression: {}", text, e);
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static String formatWithSeparators(BigDecimal number) {
      String plain = number.toPlainString();
      String[] parts = plain.split("\\.");
      String integerPart = parts[0];
      String decimalPart = parts.length > 1 ? parts[1] : "";
      StringBuilder formatted = new StringBuilder();
      int length = integerPart.length();
      boolean isNegative = integerPart.startsWith("-");
      if (isNegative) {
         formatted.append("-");
         integerPart = integerPart.substring(1);
         --length;
      }

      for(int i = 0; i < length; ++i) {
         if (i > 0 && (length - i) % 3 == 0) {
            formatted.append(".");
         }

         formatted.append(integerPart.charAt(i));
      }

      if (!decimalPart.isEmpty() && !decimalPart.equals("0")) {
         formatted.append(",").append(decimalPart);
      }

      return formatted.toString();
   }
}
