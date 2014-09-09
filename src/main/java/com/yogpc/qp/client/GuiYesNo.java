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

package com.yogpc.qp.client;

import net.minecraft.client.gui.GuiYesNoCallback;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiYesNo extends net.minecraft.client.gui.GuiYesNo {

  public GuiYesNo(final GuiYesNoCallback par1GuiScreen, final String par2Str, final String par3Str,
      final int par4) {
    super(par1GuiScreen, par2Str, par3Str, par4);
  }

  public GuiYesNo(final GuiYesNoCallback par1GuiScreen, final String par2Str, final String par3Str,
      final String par4Str, final String par5Str, final int par6) {
    super(par1GuiScreen, par2Str, par3Str, par4Str, par5Str, par6);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }

  @Override
  public void updateScreen() {
    super.updateScreen();
    if (!this.mc.thePlayer.isEntityAlive() || this.mc.thePlayer.isDead)
      this.mc.thePlayer.closeScreen();
  }
}
