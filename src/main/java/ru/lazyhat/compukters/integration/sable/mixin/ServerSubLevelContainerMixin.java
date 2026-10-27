/*
 * The Compukters Developers
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 * Licensed under the Apache License, Version 2.0.
 */
package ru.lazyhat.compukters.integration.sable.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralWorldIntegration;

/** Bulk plot destruction has no individual backing-block removal callbacks. Unload preserves saves. */
@Mixin(value = ServerSubLevelContainer.class, remap = false)
public abstract class ServerSubLevelContainerMixin {
    @Inject(method = "removeSubLevel(IILdev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V", at = @At("HEAD"))
    private void compukters$retireDeletedDisplays(int x, int z, SubLevelRemovalReason reason, CallbackInfo ci) {
        if (reason != SubLevelRemovalReason.REMOVED) return;
        var container = (ServerSubLevelContainer) (Object) this;
        var body = container.getSubLevel(x, z);
        if (body == null) return;
        var level = container.getLevel();
        CompuktersPeripheralWorldIntegration.removeBlocks(level,
                position -> Sable.HELPER.getContaining(level, position) == body);
    }
}
