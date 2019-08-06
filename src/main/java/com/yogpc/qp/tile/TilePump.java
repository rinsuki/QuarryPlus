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

package com.yogpc.qp.tile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.IFluidHandler;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.yogpc.qp.PacketHandler;
import com.yogpc.qp.PowerManager;
import com.yogpc.qp.QuarryPlus;
import com.yogpc.qp.QuarryPlusI;
import com.yogpc.qp.YogpstopPacket;

public class TilePump extends APacketTile implements IFluidHandler, IEnchantableTile {
  private ForgeDirection connectTo = ForgeDirection.UNKNOWN;
  private boolean initialized = false;

  private byte prev = (byte) ForgeDirection.UNKNOWN.ordinal();

  protected byte unbreaking;
  protected byte fortune;
  protected boolean silktouch;

  public TileBasic G_connected() {
    final TileEntity te =
        this.worldObj.getTileEntity(this.xCoord + this.connectTo.offsetX, this.yCoord
            + this.connectTo.offsetY, this.zCoord + this.connectTo.offsetZ);
    if (te instanceof TileBasic)
      return (TileBasic) te;
    this.connectTo = ForgeDirection.UNKNOWN;
    if (!this.worldObj.isRemote)
      S_sendNowPacket();
    return null;
  }

  public boolean G_working() {
    return this.py >= this.cy;
  }

  @Override
  public void readFromNBT(final NBTTagCompound nbttc) {
    super.readFromNBT(nbttc);
    this.silktouch = nbttc.getBoolean("silktouch");
    this.fortune = nbttc.getByte("fortune");
    this.unbreaking = nbttc.getByte("unbreaking");
    this.connectTo = ForgeDirection.values()[nbttc.getByte("connectTo")];
    if (nbttc.getTag("mapping0") instanceof NBTTagList)
      for (int i = 0; i < this.mapping.length; i++)
        readStringCollection(
            nbttc.getTagList(new StringBuilder().append("mapping").append(i).toString(), 8),
            this.mapping[i]);
    this.range = nbttc.getByte("range");
    this.quarryRange = nbttc.getBoolean("quarryRange");
    this.prev = (byte) (this.connectTo.ordinal() | (G_working() ? 0x80 : 0));
    if (this.silktouch) {
      this.liquids.clear();
      final NBTTagList nbttl = nbttc.getTagList("liquids", 10);
      for (int i = 0; i < nbttl.tagCount(); i++)
        this.liquids.add(FluidStack.loadFluidStackFromNBT(nbttl.getCompoundTagAt(i)));
    }
  }

  private static void readStringCollection(final NBTTagList nbttl, final Collection<String> target) {
    target.clear();
    for (int i = 0; i < nbttl.tagCount(); i++)
      target.add(nbttl.getStringTagAt(i));
  }

  @Override
  public void writeToNBT(final NBTTagCompound nbttc) {
    super.writeToNBT(nbttc);
    nbttc.setBoolean("silktouch", this.silktouch);
    nbttc.setByte("fortune", this.fortune);
    nbttc.setByte("unbreaking", this.unbreaking);
    nbttc.setByte("connectTo", (byte) this.connectTo.ordinal());
    for (int i = 0; i < this.mapping.length; i++)
      nbttc.setTag(new StringBuilder().append("mapping").append(i).toString(),
          writeStringCollection(this.mapping[i]));
    nbttc.setByte("range", this.range);
    nbttc.setBoolean("quarryRange", this.quarryRange);
    if (this.silktouch) {
      final NBTTagList nbttl = new NBTTagList();
      for (final FluidStack l : this.liquids)
        nbttl.appendTag(l.writeToNBT(new NBTTagCompound()));
      nbttc.setTag("liquids", nbttl);
    }
  }

  private static NBTTagList writeStringCollection(final Collection<String> target) {
    final NBTTagList nbttl = new NBTTagList();
    for (final String l : target)
      nbttl.appendTag(new NBTTagString(l));
    return nbttl;
  }

  @Override
  public void updateEntity() {
    super.updateEntity();
    int pX, pY, pZ;
    TileEntity te;
    FluidStack fs;
    for (final ForgeDirection fd : ForgeDirection.VALID_DIRECTIONS) {
      te =
          this.worldObj.getTileEntity(this.xCoord + fd.offsetX, this.yCoord + fd.offsetY,
              this.zCoord + fd.offsetZ);
      if (te instanceof IFluidHandler)
        for (final String s : this.mapping[fd.ordinal()]) {
          pZ = this.liquids.indexOf(FluidRegistry.getFluidStack(s, 0));
          if (pZ == -1)
            continue;
          fs = this.liquids.get(pZ);
          fs.amount -= ((IFluidHandler) te).fill(fd.getOpposite(), fs, true);
          break;
        }
    }
    if (this.worldObj.isRemote || this.initialized)
      return;
    pX = this.xCoord + this.connectTo.offsetX;
    pY = this.yCoord + this.connectTo.offsetY;
    pZ = this.zCoord + this.connectTo.offsetZ;
    te = this.worldObj.getTileEntity(pX, pY, pZ);
    if (te instanceof TileBasic && ((TileBasic) te).S_connect(this.connectTo.getOpposite())) {
      S_sendNowPacket();
      this.initialized = true;
    } else if (this.worldObj.isAirBlock(pX, pY, pZ) || this.connectTo == ForgeDirection.UNKNOWN) {
      this.connectTo = ForgeDirection.UNKNOWN;
      S_sendNowPacket();
      this.initialized = true;
    }
  }

  @Override
  public void G_reinit() {
    if (this.worldObj.isRemote)
      return;
    TileEntity te;
    for (final ForgeDirection fd : ForgeDirection.VALID_DIRECTIONS) {
      te =
          this.worldObj.getTileEntity(this.xCoord + fd.offsetX, this.yCoord + fd.offsetY,
              this.zCoord + fd.offsetZ);
      if (te instanceof TileBasic && ((TileBasic) te).S_connect(fd.getOpposite())) {
        this.connectTo = fd;
        S_sendNowPacket();
        return;
      }
    }
    this.connectTo = ForgeDirection.UNKNOWN;
    S_sendNowPacket();
    return;
  }

  private void S_sendNowPacket() {
    final byte c = (byte) (this.connectTo.ordinal() | (G_working() ? 0x80 : 0));
    if (c != this.prev) {
      this.prev = c;
      PacketHandler.sendNowPacket(this, c);
    }
  }

  @Override
  public void S_recievePacket(final byte id, final byte[] data, final EntityPlayer ep) {
    final ByteArrayDataInput badi = ByteStreams.newDataInput(data);
    byte target;
    int pos;
    String buf;
    switch (id) {
      case PacketHandler.CtS_ADD_MAPPING:// BLjava.lang.String;
        target = badi.readByte();
        this.mapping[target].add(badi.readUTF());
        S_OpenGUI(target, ep);
        break;
      case PacketHandler.CtS_REMOVE_MAPPING:// BLjava.lang.String;
        target = badi.readByte();
        this.mapping[target].remove(badi.readUTF());
        S_OpenGUI(target, ep);
        break;
      case PacketHandler.CtS_UP_MAPPING:// BLjava.lang.String;
        target = badi.readByte();
        pos = this.mapping[target].indexOf(badi.readUTF());
        if (pos > 0) {
          buf = this.mapping[target].get(pos);
          this.mapping[target].remove(pos);
          this.mapping[target].add(pos - 1, buf);
        }
        S_OpenGUI(target, ep);
        break;
      case PacketHandler.CtS_DOWN_MAPPING:// BLjava.lang.String;
        target = badi.readByte();
        pos = this.mapping[target].indexOf(badi.readUTF());
        if (pos >= 0 && pos + 1 < this.mapping[target].size()) {
          buf = this.mapping[target].get(pos);
          this.mapping[target].remove(pos);
          this.mapping[target].add(pos + 1, buf);
        }
        S_OpenGUI(target, ep);
        break;
      case PacketHandler.CtS_TOP_MAPPING:// BLjava.lang.String;
        target = badi.readByte();
        pos = this.mapping[target].indexOf(badi.readUTF());
        if (pos >= 0) {
          buf = this.mapping[target].get(pos);
          this.mapping[target].remove(pos);
          this.mapping[target].addFirst(buf);
        }
        S_OpenGUI(target, ep);
        break;
      case PacketHandler.CtS_BOTTOM_MAPPING:// BLjava.lang.String;
        target = badi.readByte();
        pos = this.mapping[target].indexOf(badi.readUTF());
        if (pos >= 0) {
          buf = this.mapping[target].get(pos);
          this.mapping[target].remove(pos);
          this.mapping[target].addLast(buf);
        }
        S_OpenGUI(target, ep);
        break;
      case PacketHandler.CtS_RENEW_DIRECTION:
        S_OpenGUI(badi.readByte(), ep);
        break;
      case PacketHandler.CtS_COPY_MAPPING:
        final byte from = badi.readByte();
        target = badi.readByte();
        this.mapping[target].clear();
        this.mapping[target].addAll(this.mapping[from]);
        S_OpenGUI(target, ep);
        break;
    }
  }

  @Override
  public void C_recievePacket(final byte id, final byte[] data, final EntityPlayer ep) {
    final ByteArrayDataInput badi = ByteStreams.newDataInput(data);
    switch (id) {
      case PacketHandler.StC_NOW:// B
        final byte flag = badi.readByte();
        if ((flag & 0x80) != 0)
          this.cy = this.py = -1;
        else
          this.py = Integer.MIN_VALUE;
        this.connectTo = ForgeDirection.getOrientation(flag & 0x7F);
        this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        break;
      case PacketHandler.StC_OPENGUI_MAPPING:// BI[Ljava.lang.String;
        final byte target = badi.readByte();
        final int len = badi.readInt();
        this.mapping[target].clear();
        for (int i = 0; i < len; i++)
          this.mapping[target].add(badi.readUTF());
        ep.openGui(QuarryPlus.I, QuarryPlusI.guiIdPump + target, this.worldObj, this.xCoord,
            this.yCoord, this.zCoord);
        break;
    }
  }

  public void S_OpenGUI(final int d, final EntityPlayer ep) {// BI[Ljava.lang.String;
    try {
      final ByteArrayOutputStream bos = new ByteArrayOutputStream();
      final DataOutputStream dos = new DataOutputStream(bos);
      dos.writeByte(d);
      dos.writeInt(this.mapping[d].size());
      for (final String s : this.mapping[d])
        dos.writeUTF(s);
      PacketHandler.sendPacketToPlayer(new YogpstopPacket(bos.toByteArray(), this,
          PacketHandler.StC_OPENGUI_MAPPING), ep);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  private static final int Y_SIZE = 256;
  private static final int CHUNK_SCALE = 16;

  private byte[][][] blocks;
  private ExtendedBlockStorage[][][] ebses;
  private int xOffset, yOffset, zOffset, px, py = Integer.MIN_VALUE;
  private int cx, cy = -1, cz;
  private byte range = 0;
  private boolean quarryRange = true;

  private int block_side_x, block_side_z;

  private static final int ARRAY_MAX = 0x80000;
  private static final int[] xb = new int[ARRAY_MAX];
  private static final int[] yb = new int[ARRAY_MAX];
  private static final int[] zb = new int[ARRAY_MAX];
  private static int cp = 0, cg = 0;
  private long fwt;

  public void S_changeRange(final EntityPlayer ep) {
    if (this.range >= (this.fortune + 1) * 2) {
      if (G_connected() instanceof TileQuarry)
        this.quarryRange = true;
      this.range = 0;
    } else if (this.quarryRange)
      this.quarryRange = false;
    else
      this.range++;
    if (this.quarryRange)
      ep.addChatMessage(new ChatComponentTranslation("chat.pump_rtoggle.quarry"));
    else
      ep.addChatMessage(new ChatComponentTranslation("chat.pump_rtoggle.num", Integer
          .toString(this.range * 2 + 1)));
    this.fwt = 0;
  }

  private static void S_put(final int x, final int y, final int z) {
    xb[cp] = x;
    yb[cp] = y;
    zb[cp] = z;
    cp++;
    if (cp == ARRAY_MAX)
      cp = 0;
  }

  private void S_searchLiquid(final int x, final int y, final int z) {
    this.fwt = this.worldObj.getWorldTime();
    cp = cg = 0;
    int chunk_side_x, chunk_side_z;
    this.cx = x;
    this.cy = y;
    this.cz = z;
    this.yOffset = y & 0xFFFFFFF0;
    this.py = Y_SIZE - 1;
    this.px = -1;
    final TileBasic tb = G_connected();
    TileQuarry b = null;
    if (tb instanceof TileQuarry)
      b = (TileQuarry) tb;
    if (b != null && b.yMax != Integer.MIN_VALUE) {
      chunk_side_x = 1 + (b.xMax >> 4) - (b.xMin >> 4);
      chunk_side_z = 1 + (b.zMax >> 4) - (b.zMin >> 4);
      this.xOffset = b.xMin & 0xFFFFFFF0;
      this.zOffset = b.zMin & 0xFFFFFFF0;
      final int x_add = this.range * 2 + 1 - chunk_side_x;
      if (x_add > 0) {
        chunk_side_x += x_add;
        this.xOffset -=
            ((x_add & 0xFFFFFFFE) << 3)
                + (x_add % 2 != 0 && (b.xMax + b.xMin + 1) / 2 % 0x10 <= 8 ? 0x10 : 0);
      }
      final int z_add = this.range * 2 + 1 - chunk_side_z;
      if (z_add > 0) {
        chunk_side_z += z_add;
        this.zOffset -=
            ((z_add & 0xFFFFFFFE) << 3)
                + (z_add % 2 != 0 && (b.zMax + b.zMin + 1) / 2 % 0x10 <= 8 ? 0x10 : 0);
      }
    } else {
      this.quarryRange = false;
      chunk_side_x = chunk_side_z = 1 + this.range * 2;
      this.xOffset = (x >> 4) - this.range << 4;
      this.zOffset = (z >> 4) - this.range << 4;

    }
    if (!this.quarryRange)
      b = null;
    this.block_side_x = chunk_side_x * CHUNK_SCALE;
    this.block_side_z = chunk_side_z * CHUNK_SCALE;
    this.blocks = new byte[Y_SIZE - this.yOffset][this.block_side_x][this.block_side_z];
    this.ebses = new ExtendedBlockStorage[chunk_side_x][chunk_side_z][];
    int kx, kz;
    for (kx = 0; kx < chunk_side_x; kx++)
      for (kz = 0; kz < chunk_side_z; kz++)
        this.ebses[kx][kz] =
            this.worldObj.getChunkProvider()
                .loadChunk(kx + (this.xOffset >> 4), kz + (this.zOffset >> 4))
                .getBlockStorageArray();
    S_put(x - this.xOffset, y, z - this.zOffset);
    Block b_c;
    ExtendedBlockStorage ebs_c;
    while (cp != cg) {
      ebs_c = this.ebses[xb[cg] >> 4][zb[cg] >> 4][yb[cg] >> 4];
      if (ebs_c != null) {
        b_c = ebs_c.getBlockByExtId(xb[cg] & 0xF, yb[cg] & 0xF, zb[cg] & 0xF);
        if (this.blocks[yb[cg] - this.yOffset][xb[cg]][zb[cg]] == 0
            && isLiquid(b_c, false, null, 0, 0, 0, 0)) {
          this.blocks[yb[cg] - this.yOffset][xb[cg]][zb[cg]] = 0x3F;

          if ((b != null ? b.xMin & 0xF : 0) < xb[cg])
            S_put(xb[cg] - 1, yb[cg], zb[cg]);
          else
            this.blocks[yb[cg] - this.yOffset][xb[cg]][zb[cg]] = 0x7F;

          if (xb[cg] < (b != null ? b.xMax - this.xOffset : this.block_side_x - 1))
            S_put(xb[cg] + 1, yb[cg], zb[cg]);
          else
            this.blocks[yb[cg] - this.yOffset][xb[cg]][zb[cg]] = 0x7F;

          if ((b != null ? b.zMin & 0xF : 0) < zb[cg])
            S_put(xb[cg], yb[cg], zb[cg] - 1);
          else
            this.blocks[yb[cg] - this.yOffset][xb[cg]][zb[cg]] = 0x7F;

          if (zb[cg] < (b != null ? b.zMax - this.zOffset : this.block_side_z - 1))
            S_put(xb[cg], yb[cg], zb[cg] + 1);
          else
            this.blocks[yb[cg] - this.yOffset][xb[cg]][zb[cg]] = 0x7F;

          if (yb[cg] + 1 < Y_SIZE)
            S_put(xb[cg], yb[cg] + 1, zb[cg]);
        }
      }
      cg++;
      if (cg == ARRAY_MAX)
        cg = 0;
    }
  }

  boolean S_removeLiquids(final APowerTile tbpp, final int x, final int y, final int z) {
    S_sendNowPacket();
    if (this.cx != x || this.cy != y || this.cz != z || this.py < this.cy
        || this.worldObj.getWorldTime() - this.fwt > 200)
      S_searchLiquid(x, y, z);
    else {
      this.ebses = new ExtendedBlockStorage[this.ebses.length][this.ebses[0].length][];
      int kx, kz;
      for (kx = 0; kx < this.ebses.length; kx++)
        for (kz = 0; kz < this.ebses[0].length; kz++)
          this.ebses[kx][kz] =
              this.worldObj.getChunkProvider()
                  .loadChunk(kx + (this.xOffset >> 4), kz + (this.zOffset >> 4))
                  .getBlockStorageArray();
    }
    int count = 0;
    Block bb;
    int bz, meta;
    do {
      do {
        if (this.px == -1) {
          int bx;
          for (bx = 0; bx < this.block_side_x; bx++)
            for (bz = 0; bz < this.block_side_z; bz++)
              if ((this.blocks[this.py - this.yOffset][bx][bz] & 0x40) != 0) {
                bb =
                    this.ebses[bx >> 4][bz >> 4][this.py >> 4].getBlockByExtId(bx & 0xF,
                        this.py & 0xF, bz & 0xF);
                if (isLiquid(bb, false, null, 0, 0, 0, 0))
                  count++;
              }
        } else
          for (bz = 0; bz < this.block_side_z; bz++)
            if (this.blocks[this.py - this.yOffset][this.px][bz] != 0) {
              bb =
                  this.ebses[this.px >> 4][bz >> 4][this.py >> 4].getBlockByExtId(this.px & 0xF,
                      this.py & 0xF, bz & 0xF);
              meta =
                  this.ebses[this.px >> 4][bz >> 4][this.py >> 4].getExtBlockMetadata(
                      this.px & 0xF, this.py & 0xF, bz & 0xF);
              if (isLiquid(bb, true, this.worldObj, this.px + this.xOffset, this.py, bz
                  + this.zOffset, meta))
                count++;
            }
        if (count > 0)
          break;
      } while (++this.px < this.block_side_x);
      if (count > 0)
        break;
      this.px = -1;
    } while (--this.py >= this.cy);
    if (count > 0
        && PowerManager.useEnergyP(tbpp, this.unbreaking, count, this.px == -1 ? count : 0))
      if (this.px == -1) {
        int bx;
        for (bx = 0; bx < this.block_side_x; bx++)
          for (bz = 0; bz < this.block_side_z; bz++)
            if ((this.blocks[this.py - this.yOffset][bx][bz] & 0x40) != 0)
              drainBlock(bx, bz, Blocks.glass);
      } else
        for (bz = 0; bz < this.block_side_z; bz++)
          if (this.blocks[this.py - this.yOffset][this.px][bz] != 0)
            drainBlock(this.px, bz, Blocks.air);
    S_sendNowPacket();
    return this.py < this.cy;
  }

  // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  private final LinkedList<FluidStack> liquids = new LinkedList<FluidStack>();
  public final LinkedList<String>[] mapping =
      new LinkedList[ForgeDirection.VALID_DIRECTIONS.length];

  {
    for (int i = 0; i < this.mapping.length; i++)
      this.mapping[i] = new LinkedList<String>();
  }

  public IChatComponent[] C_getNames() {
    final IChatComponent[] ret = new IChatComponent[this.liquids.size() + 1];
    if (this.liquids.size() > 0) {
      ret[0] = new ChatComponentTranslation("chat.pumpcontain");
      for (int i = 0; i < this.liquids.size(); i++) {
        final FluidStack s = this.liquids.get(i);
        ret[i + 1] =
            new ChatComponentTranslation("yog.pump.liquid", new ChatComponentTranslation(s
                .getFluid().getUnlocalizedName(s)), Integer.toString(s.amount));
      }
    } else
      ret[0] = new ChatComponentTranslation("chat.pumpcontainno");
    return ret;
  }

  @Override
  public int fill(final ForgeDirection from, final FluidStack resource, final boolean doFill) {
    return 0;
  }

  @Override
  public FluidStack drain(final ForgeDirection fd, final FluidStack resource, final boolean doDrain) {
    if (resource == null)
      return null;
    final int index = this.liquids.indexOf(resource);
    if (index == -1)
      return null;
    final FluidStack fs = this.liquids.get(index);
    if (fs == null)
      return null;
    final FluidStack ret = fs.copy();
    ret.amount = Math.min(fs.amount, resource.amount);
    if (doDrain)
      fs.amount -= ret.amount;
    if (fs.amount <= 0)
      this.liquids.remove(fs);
    if (ret.amount <= 0)
      return null;
    return ret;
  }

  @Override
  public boolean canFill(final ForgeDirection from, final Fluid fluid) {
    return false;
  }

  @Override
  public boolean canDrain(final ForgeDirection from, final Fluid fluid) {
    return true;
  }

  @Override
  public FluidTankInfo[] getTankInfo(final ForgeDirection fd) {
    if (fd.ordinal() < 0 || fd.ordinal() >= this.mapping.length)
      return getTankInfo(ForgeDirection.UP);
    final LinkedList<FluidTankInfo> ret = new LinkedList<FluidTankInfo>();
    if (this.mapping[fd.ordinal()].size() <= 0) {
      if (this.liquids.size() <= 0)
        for (final Integer i : FluidRegistry.getRegisteredFluidIDs().values())
          ret.add(new FluidTankInfo(new FluidStack(i.intValue(), 0), Integer.MAX_VALUE));
      else
        for (final FluidStack fs : this.liquids)
          ret.add(new FluidTankInfo(fs, Integer.MAX_VALUE));
    } else {
      int index;
      FluidStack fs;
      for (final String s : this.mapping[fd.ordinal()]) {
        fs = FluidRegistry.getFluidStack(s, 0);
        if (fs == null)
          continue;
        index = this.liquids.indexOf(fs);
        if (index != -1)
          ret.add(new FluidTankInfo(this.liquids.get(index), Integer.MAX_VALUE));
        else
          ret.add(new FluidTankInfo(fs, Integer.MAX_VALUE));
      }
    }
    return ret.toArray(new FluidTankInfo[ret.size()]);
  }

  @Override
  public FluidStack drain(final ForgeDirection fd, final int maxDrain, final boolean doDrain) {
    if (fd.ordinal() < 0 || fd.ordinal() >= this.mapping.length)
      return drain(ForgeDirection.UP, maxDrain, doDrain);
    if (this.mapping[fd.ordinal()].size() <= 0) {
      if (this.liquids.size() <= 0)
        return null;
      return drain(fd, this.liquids.getFirst(), doDrain);
    }
    int index;
    FluidStack fs;
    for (final String s : this.mapping[fd.ordinal()]) {
      fs = FluidRegistry.getFluidStack(s, maxDrain);
      if (fs == null)
        continue;
      index = this.liquids.indexOf(fs);
      if (index == -1)
        continue;
      return drain(fd, this.liquids.get(index), doDrain);
    }
    return null;
  }

  // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  /**
   * @param w When source is false, it can be null.
   * @param x When source is false, it can be any value.
   * @param y When source is false, it can be any value.
   * @param z When source is false, it can be any value.
   * @param m When source is false, it can be any value.
   * 
   * */
  static final boolean isLiquid(final Block b, final boolean s, final World w, final int x,
      final int y, final int z, final int m) {
    if (b instanceof IFluidBlock)
      return !s || ((IFluidBlock) b).canDrain(w, x, y, z);
    if (b == Blocks.water || b == Blocks.flowing_water || b == Blocks.lava
        || b == Blocks.flowing_lava)
      return !s || m == 0;
    return false;
  }

  private final void drainBlock(final int bx, final int bz, final Block tb) {
    final Block b =
        this.ebses[bx >> 4][bz >> 4][this.py >> 4].getBlockByExtId(bx & 0xF, this.py & 0xF,
            bz & 0xF);
    final int meta =
        this.ebses[bx >> 4][bz >> 4][this.py >> 4].getExtBlockMetadata(bx & 0xF, this.py & 0xF,
            bz & 0xF);
    if (isLiquid(b, false, null, 0, 0, 0, 0)) {
      FluidStack fs = null;
      if (b instanceof IFluidBlock
          && ((IFluidBlock) b).canDrain(this.worldObj, bx + this.xOffset, this.py, bz
              + this.zOffset))
        fs =
            ((IFluidBlock) b).drain(this.worldObj, bx + this.xOffset, this.py, bz + this.zOffset,
                true);
      else if ((b == Blocks.water || b == Blocks.flowing_water) && meta == 0)
        fs = new FluidStack(FluidRegistry.WATER, FluidContainerRegistry.BUCKET_VOLUME);
      else if ((b == Blocks.lava || b == Blocks.flowing_lava) && meta == 0)
        fs = new FluidStack(FluidRegistry.LAVA, FluidContainerRegistry.BUCKET_VOLUME);
      if (fs != null) {
        final int index = this.liquids.indexOf(fs);
        if (index != -1)
          this.liquids.get(index).amount += fs.amount;
        else
          this.liquids.add(fs);
        fs = null;
      }
      this.worldObj.setBlock(bx + this.xOffset, this.py, bz + this.zOffset, tb);
    }
  }

  @Override
  public Map<Integer, Byte> get() {
    final Map<Integer, Byte> ret = new HashMap<Integer, Byte>();
    if (this.fortune > 0)
      ret.put(Integer.valueOf(Enchantment.fortune.effectId), Byte.valueOf(this.fortune));
    if (this.unbreaking > 0)
      ret.put(Integer.valueOf(Enchantment.unbreaking.effectId), Byte.valueOf(this.unbreaking));
    if (this.silktouch)
      ret.put(Integer.valueOf(Enchantment.silkTouch.effectId), Byte.valueOf((byte) 1));
    return ret;
  }

  @Override
  public void set(final int id, final byte val) {
    if (id == Enchantment.fortune.effectId)
      this.fortune = val;
    else if (id == Enchantment.unbreaking.effectId)
      this.unbreaking = val;
    else if (id == Enchantment.silkTouch.effectId && val > 0)
      this.silktouch = true;
  }
}
