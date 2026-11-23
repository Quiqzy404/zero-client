package dev.quiqzy.client.render;

/** Wird per Mixin in EntityRenderState eingebaut: Chams-Farbe pro Render-State (-1 = keine). */
public interface ChamsState {
	int zero$chams();

	void zero$setChams(int rgb);
}
