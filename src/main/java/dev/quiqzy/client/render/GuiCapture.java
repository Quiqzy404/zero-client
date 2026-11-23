package dev.quiqzy.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Nimmt ein Rechteck aus dem Haupt-Framebuffer (die echte GUI) in eine eigene, registrierte Textur auf
 * (copyTextureToTexture, GPU->GPU). Die Textur kann dann per entityCutoutNoCull(ID) auf ein Welt-Quad.
 */
public final class GuiCapture extends AbstractTexture {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("quiqzy", "gui_capture");
	private static GuiCapture inst;
	private int w, h;
	public static volatile boolean has;
	public static int capW, capH;

	private GuiCapture() {
	}

	public static GuiCapture get() {
		if (inst == null) {
			inst = new GuiCapture();
			Minecraft.getInstance().getTextureManager().register(ID, inst);
		}
		return inst;
	}

	private void ensure(int cw, int ch) {
		if (texture != null && cw == w && ch == h) return;
		if (textureView != null) textureView.close();
		if (texture != null) texture.close();
		w = cw;
		h = ch;
		var dev = RenderSystem.getDevice();
		texture = dev.createTexture("zero gui cap", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				TextureFormat.RGBA8, cw, ch, 1, 1);
		textureView = dev.createTextureView(texture);
		sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}

	/** Rechteck (sx,sy,cw,ch) aus dem Haupt-Framebuffer in die Textur kopieren. */
	public static void capture(int sx, int sy, int cw, int ch) {
		if (cw <= 0 || ch <= 0) return;
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		GpuTexture src = main.getColorTexture();
		if (src == null) return;
		sx = Math.max(0, Math.min(sx, main.width - 1));
		sy = Math.max(0, Math.min(sy, main.height - 1));
		cw = Math.min(cw, main.width - sx);
		ch = Math.min(ch, main.height - sy);
		GuiCapture c = get();
		c.ensure(cw, ch);
		try {
			RenderSystem.getDevice().createCommandEncoder()
					.copyTextureToTexture(src, c.texture, 0, sx, sy, 0, 0, cw, ch);
			capW = cw;
			capH = ch;
			has = true;
		} catch (Throwable t) {
			has = false;
		}
	}
}
