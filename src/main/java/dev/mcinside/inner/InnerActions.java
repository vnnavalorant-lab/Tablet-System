package dev.mcinside.inner;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Ataque, uso, hotbar etc. do jogador interno (replicam MinecraftClient.doAttack/doItemUse). Sempre dentro do contexto. */
final class InnerActions {
    private InnerActions() { }

    static void tick(InnerSession s) {
        ClientPlayerEntity p = s.player;
        if (p == null || s.innerScreen != null) { if (s.im != null) s.im.cancelBlockBreaking(); return; }
        if (s.useCooldown > 0) s.useCooldown--;
        HitResult h = s.client.crosshairTarget;
        if (s.leftHeld && h != null && h.getType() == HitResult.Type.BLOCK) {
            BlockHitResult b = (BlockHitResult) h;
            BlockPos pos = b.getBlockPos();
            if (!s.world.getBlockState(pos).isAir()) {
                if (s.im.updateBlockBreakingProgress(pos, b.getSide())) {
                    s.client.particleManager.addBlockBreakingParticles(pos, b.getSide());
                    p.swingHand(Hand.MAIN_HAND);
                }
            }
        } else if (s.im != null) {
            s.im.cancelBlockBreaking();
        }
        if (s.rightHeld && s.useCooldown == 0) use(s);
    }

    static void attackPressed(InnerSession s) {
        ClientPlayerEntity p = s.player;
        if (p == null || s.innerScreen != null) return;
        HitResult h = s.client.crosshairTarget;
        if (h != null && h.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) h).getEntity();
            s.im.attackEntity(p, e);
        }
        p.swingHand(Hand.MAIN_HAND);
    }

    static void use(InnerSession s) {
        ClientPlayerEntity p = s.player;
        if (p == null || s.innerScreen != null || s.im.isBreakingBlock()) return;
        s.useCooldown = 4;
        HitResult h = s.client.crosshairTarget;
        for (Hand hand : Hand.values()) {
            ItemStack stack = p.getStackInHand(hand);
            if (h != null) {
                if (h.getType() == HitResult.Type.ENTITY) {
                    Entity e = ((EntityHitResult) h).getEntity();
                    ActionResult r = s.im.interactEntity(p, e, hand);
                    if (r.isAccepted()) { if (r.shouldSwingHand()) p.swingHand(hand); return; }
                } else if (h.getType() == HitResult.Type.BLOCK) {
                    ActionResult r = s.im.interactBlock(p, hand, (BlockHitResult) h);
                    if (r.isAccepted()) { if (r.shouldSwingHand()) p.swingHand(hand); return; }
                    if (r == ActionResult.FAIL) return;
                }
            }
            if (!stack.isEmpty() && s.im.interactItem(p, hand).isAccepted()) return;
        }
    }

    static void selectSlot(InnerSession s, int slot) { if (s.player != null) s.player.getInventory().selectedSlot = slot; }

    static void scroll(InnerSession s, double amount) { if (s.player != null) s.player.getInventory().scrollInHotbar(amount); }

    static void drop(InnerSession s, boolean all) {
        if (s.player != null && s.player.dropSelectedItem(all)) s.player.swingHand(Hand.MAIN_HAND);
    }

    static void swapHands(InnerSession s) {
        if (s.player != null && !s.player.isSpectator())
            s.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
    }

    static void openInventory(InnerSession s) {
        if (s.player != null) s.onInnerSetScreen(new InventoryScreen(s.player));
    }
}
