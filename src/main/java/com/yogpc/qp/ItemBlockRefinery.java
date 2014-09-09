/*
 * Copyright (C) 2012,2013 yogpstop This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.yogpc.qp;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemBlockRefinery extends ItemBlock implements IEnchantableItem {

  public ItemBlockRefinery(final Block b) {
    super(b);
  }

  @Override
  public boolean canMove(final ItemStack is, final int id, final int meta) {
    return id == 32 || id == 34 || id == 35 || id == -1;
  }

  @Override
  public boolean isBookEnchantable(final ItemStack itemstack1, final ItemStack itemstack2) {
    return false;
  }
}
