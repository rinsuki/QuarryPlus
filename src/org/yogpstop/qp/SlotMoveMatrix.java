package org.yogpstop.qp;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class SlotMoveMatrix extends Slot {
	Container parentsC;

	public SlotMoveMatrix(IInventory par1iInventory, int par2, int par3,
			int par4, Container c) {
		super(par1iInventory, par2, par3, par4);
		parentsC = c;

	}

	@Override
	public boolean isItemValid(ItemStack par1ItemStack) {
		switch (this.slotNumber) {
		case 0:
			if (par1ItemStack.itemID == Item.pickaxeDiamond.shiftedIndex) {
				return true;
			}
			return false;
		case 1:
			if (par1ItemStack.itemID == QuarryPlus.itemBase.shiftedIndex) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onSlotChanged() {
		parentsC.onCraftMatrixChanged(inventory);
		super.onSlotChanged();
	}
}
