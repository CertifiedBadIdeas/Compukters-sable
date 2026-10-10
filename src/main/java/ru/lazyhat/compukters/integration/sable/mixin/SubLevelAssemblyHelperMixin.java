/*
 * The Compukters Developers
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 * Licensed under the Apache License, Version 2.0.
 */

package ru.lazyhat.compukters.integration.sable.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralWorldIntegration;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** One scope surrounds the whole move, including source removal and exceptional completion. */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyHelperMixin {
    @WrapMethod(method = "moveBlocks")
    private static void compukters$preservePeripheralBindings(
            ServerLevel source, SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> positions, Operation<Void> original) throws Exception {
        var copied = new ArrayList<BlockPos>();
        var mapping = new LinkedHashMap<BlockPos, BlockPos>();
        for (var position : positions) {
            var from = position.immutable();
            copied.add(from);
            mapping.put(from, transform.apply(from).immutable());
        }
        try (var transfer = CompuktersPeripheralWorldIntegration.beginTransfer(source, transform.getLevel(), mapping)) {
            original.call(source, transform, copied);
        }
    }
}
