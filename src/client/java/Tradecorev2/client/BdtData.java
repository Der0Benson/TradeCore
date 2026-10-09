package Tradecorev2.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class BdtData {
   public int bdt_id;
   public String item_id;
   public String item_name;
   public String gewinn;
   public int schnell_votes;
   public int langsam_votes;
   public boolean has_voted;
}
