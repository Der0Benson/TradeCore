package Tradecorev2.client.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class CalculatorScreen extends Screen {
   private static final Logger LOGGER = LoggerFactory.getLogger("TradecoreV2-CalculatorScreen");
   private EditBox display;
   private String currentNumber = "";
   private String previousNumber = "";
   private String operation = "";
   private boolean shouldResetDisplay = false;
   private static final int ITEMS_PER_STACK = 64;
   private static final int ITEMS_PER_CHEST = 1728;
   private static final int ITEMS_PER_DOUBLE_CHEST = 3456;

   public CalculatorScreen() {
      super(Component.literal("Taschenrechner"));
   }

   protected void init() {
      super.init();
      int panelWidth = 220;
      int panelHeight = 245;
      int startX = (this.width - panelWidth) / 2;
      int startY = (this.height - panelHeight) / 2;
      this.display = new EditBox(this.font, startX + 10, startY + 10, panelWidth - 20, 20, Component.literal(""));
      this.display.setEditable(false);
      this.display.setValue("0");
      this.addRenderableWidget(this.display);
      int buttonWidth = 40;
      int buttonHeight = 20;
      int spacing = 5;
      int buttonX = startX + 10;
      int buttonY = startY + 40;
      String[][] buttons = new String[][]{{"7", "8", "9", "/"}, {"4", "5", "6", "*"}, {"1", "2", "3", "-"}, {"0", ".", "=", "+"}};

      for(int row = 0; row < buttons.length; ++row) {
         for(int col = 0; col < buttons[row].length; ++col) {
            String text = buttons[row][col];
            Button button = Button.builder(Component.literal(text), (btn) -> this.onButtonClick(text)).bounds(buttonX + col * (buttonWidth + spacing), buttonY + row * (buttonHeight + spacing), buttonWidth, buttonHeight).build();
            this.addRenderableWidget(button);
         }
      }

      String[] unitButtons = new String[]{"C", "Stack", "Kiste", "D.Kiste"};
      int unitButtonY = buttonY + buttons.length * (buttonHeight + spacing);

      for(int col = 0; col < unitButtons.length; ++col) {
         String text = unitButtons[col];
         Button button = Button.builder(Component.literal(text), (btn) -> this.onButtonClick(text)).bounds(buttonX + col * (buttonWidth + spacing), unitButtonY, buttonWidth, buttonHeight).build();
         this.addRenderableWidget(button);
      }

      this.addRenderableWidget(Button.builder(Component.literal("Fertig"), (buttonx) -> this.onClose()).bounds(startX + (panelWidth - 100) / 2, startY + panelHeight - 28, 100, 20).build());
   }

   private void onButtonClick(String text) {
      if (!Character.isDigit(text.charAt(0)) && !text.equals(".")) {
         if (text.equals("=")) {
            this.calculate();
         } else if (text.equals("C")) {
            this.clear();
         } else if (!text.equals("Stack") && !text.equals("Kiste") && !text.equals("D.Kiste")) {
            this.handleOperation(text);
         } else {
            this.handleUnitConversion(text);
         }
      } else {
         this.handleNumberInput(text);
      }

   }

   private void handleNumberInput(String number) {
      if (this.shouldResetDisplay) {
         this.currentNumber = "";
         this.shouldResetDisplay = false;
      }

      if (!number.equals(".") || !this.currentNumber.contains(".")) {
         if (this.currentNumber.equals("0") && !number.equals(".")) {
            this.currentNumber = number;
         } else {
            this.currentNumber = this.currentNumber + number;
         }

         this.display.setValue(this.currentNumber);
      }
   }

   private void handleOperation(String op) {
      if (!this.currentNumber.isEmpty()) {
         if (!this.previousNumber.isEmpty() && !this.operation.isEmpty()) {
            this.calculate();
         }

         this.operation = op;
         this.previousNumber = this.currentNumber;
         this.currentNumber = "";
         this.shouldResetDisplay = true;
      } else if (!this.previousNumber.isEmpty() && this.operation.isEmpty()) {
         this.operation = op;
         this.shouldResetDisplay = true;
      }

   }

   private void calculate() {
      if (!this.previousNumber.isEmpty() && !this.currentNumber.isEmpty() && !this.operation.isEmpty()) {
         BigDecimal prev;
         BigDecimal curr;
         try {
            prev = new BigDecimal(this.previousNumber);
            curr = new BigDecimal(this.currentNumber);
         } catch (NumberFormatException e) {
            this.display.setValue("Error");
            LOGGER.error("Invalid number format in calculate: prev='{}', curr='{}'", new Object[]{this.previousNumber, this.currentNumber, e});
            this.clear();
            return;
         }

         BigDecimal result;
         try {
            switch (this.operation) {
               case "+":
                  result = prev.add(curr);
                  break;
               case "-":
                  result = prev.subtract(curr);
                  break;
               case "*":
                  result = prev.multiply(curr);
                  break;
               case "/":
                  if (curr.compareTo(BigDecimal.ZERO) == 0) {
                     this.display.setValue("Error");
                     this.clear();
                     return;
                  }

                  result = prev.divide(curr, 8, RoundingMode.HALF_UP);
                  break;
               default:
                  this.display.setValue("Error");
                  this.clear();
                  return;
            }
         } catch (ArithmeticException e) {
            this.display.setValue("Error");
            LOGGER.error("Arithmetic error in calculate: {}", e.getMessage());
            this.clear();
            return;
         }

         String resultText = result.stripTrailingZeros().toPlainString();
         this.currentNumber = resultText;
         this.operation = "";
         this.previousNumber = "";
         this.display.setValue(resultText);
         this.shouldResetDisplay = true;
      }
   }

   private void clear() {
      this.currentNumber = "";
      this.previousNumber = "";
      this.operation = "";
      this.display.setValue("0");
      this.shouldResetDisplay = false;
   }

   private void handleUnitConversion(String unitType) {
      BigDecimal value;
      if (!this.currentNumber.isEmpty() && !this.currentNumber.equals(".")) {
         try {
            value = new BigDecimal(this.currentNumber);
         } catch (NumberFormatException e) {
            this.display.setValue("Error");
            LOGGER.error("Invalid number format for unit conversion: '{}'", this.currentNumber, e);
            this.clear();
            return;
         }
      } else {
         value = BigDecimal.ONE;
      }

      BigDecimal multiplier;
      switch (unitType) {
         case "Stack" -> multiplier = new BigDecimal(64);
         case "Kiste" -> multiplier = new BigDecimal(1728);
         case "D.Kiste" -> multiplier = new BigDecimal(3456);
         default -> {
            return;
         }
      }

      BigDecimal result = value.multiply(multiplier);
      String resultText = result.stripTrailingZeros().toPlainString();
      this.currentNumber = resultText;
      this.previousNumber = "";
      this.operation = "";
      this.display.setValue(resultText);
      this.shouldResetDisplay = true;
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int panelWidth = 220;
      int panelHeight = 245;
      int startX = (this.width - panelWidth) / 2;
      int startY = (this.height - panelHeight) / 2;
      context.fill(startX, startY, startX + panelWidth, startY + panelHeight, -534765536);
      context.outline(startX, startY, panelWidth, panelHeight, -1);
      Component titleText = this.getTitle();
      int titleWidth = this.font.width(titleText);
      context.text(this.font, titleText, startX + (panelWidth - titleWidth) / 2, startY - 15, -1);
      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public boolean charTyped(CharacterEvent event) {
      return false;
   }

   public boolean keyPressed(KeyEvent event) {
      if (event.key() == InputConstants.KEY_ESCAPE) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(event);
      }
   }
}
