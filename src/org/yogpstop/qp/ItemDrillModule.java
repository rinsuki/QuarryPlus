package org.yogpstop.qp;

import net.minecraft.src.CreativeTabs;
import net.minecraft.src.Item;

public class ItemDrillModule extends Item {

	public ItemDrillModule(int par1) {
		super(par1);
		this.iconIndex = 4;
		this.setItemName("DrillModule");
		this.setCreativeTab(CreativeTabs.tabRedstone);
	}
	
	@Override
	public String getTextureFile(){
		return "/org/yogpstop/qp/blocks.png";
	}

}
