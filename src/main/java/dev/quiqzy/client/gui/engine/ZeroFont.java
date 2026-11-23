package dev.quiqzy.client.gui.engine;

import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class ZeroFont {
   public static final ZeroFont REGULAR = new ZeroFont("poppins.ttf", latin(), 0);
   // Poppins Medium ist nur als Regular geschnitten – Fett per SDF-Pseudo-Bold (Kante verschieben)
   public static final ZeroFont BOLD = new ZeroFont("poppins.ttf", latin(), 18);
   public static final ZeroFont LOGO = new ZeroFont("playfair_italic.ttf", ascii(), 0);
   public static final ZeroFont ICONS = new ZeroFont("zero_icons.ttf", ZeroFont.Icon.ALL, 0);
   private static final int ATLAS = 1024;
   private static final int MIPS = 3;
   private static final float EM_PX = 42.0F;
   private static final int PAD = 6;
   private final String file;
   private final String chars;
   /** Pseudo-Bold: wie stark die SDF-Kante nach aussen wandert (0 = normale Strichstaerke). */
   private final int boldBias;
   private final Map<Integer, ZeroFont.Glyph> glyphs = new HashMap<>();
   private STBTTFontinfo info;
   private ByteBuffer ttf;
   private float ascent;
   private float descent;
   private float scale;
   private GpuTexture texture;
   private GpuTextureView view;

   private ZeroFont(String file, String chars, int boldBias) {
      this.file = file;
      this.chars = chars;
      this.boldBias = boldBias;
   }

   GpuTextureView view() {
      this.ensureLoaded();
      return this.view;
   }

   float ascent() {
      this.ensureLoaded();
      return this.ascent;
   }

   float descent() {
      this.ensureLoaded();
      return this.descent;
   }

   ZeroFont.Glyph glyph(int codepoint) {
      this.ensureLoaded();
      ZeroFont.Glyph g = this.glyphs.get(codepoint);
      return g != null ? g : this.glyphs.get(63);
   }

   float kerning(int first, int second) {
      return first < 0 ? 0.0F : STBTruetype.stbtt_GetGlyphKernAdvance(this.info, first, second) * this.scale;
   }

   public float width(String text, float size, float tracking) {
      this.ensureLoaded();
      float w = 0.0F;
      int previous = -1;
      int i = 0;

      while (i < text.length()) {
         int cp = text.codePointAt(i);
         i += Character.charCount(cp);
         ZeroFont.Glyph g = this.glyph(cp);
         if (g != null) {
            w += (this.kerning(previous, g.index) + g.advance) * size + tracking;
            previous = g.index;
         }
      }

      return w;
   }

   private void ensureLoaded() {
      if (this.texture == null) {
         RenderSystem.assertOnRenderThread();

         try (InputStream in = ZeroFont.class.getResourceAsStream("/assets/quiqzy/font/" + this.file)) {
            if (in == null) {
               throw new IOException("nicht gefunden");
            }

            byte[] bytes = in.readAllBytes();
            this.ttf = MemoryUtil.memAlloc(bytes.length);
            this.ttf.put(bytes).flip();
         } catch (IOException var10) {
            throw new IllegalStateException("Schrift " + this.file + " konnte nicht geladen werden", var10);
         }

         this.info = STBTTFontinfo.malloc();
         if (!STBTruetype.stbtt_InitFont(this.info, this.ttf)) {
            throw new IllegalStateException("Ungueltige Schrift: " + this.file);
         } else {
            float pixelScale = STBTruetype.stbtt_ScaleForMappingEmToPixels(this.info, 42.0F);
            this.scale = pixelScale / 42.0F;
            MemoryStack stack = MemoryStack.stackPush();

            try {
               IntBuffer asc = stack.mallocInt(1);
               IntBuffer desc = stack.mallocInt(1);
               IntBuffer gap = stack.mallocInt(1);
               STBTruetype.stbtt_GetFontVMetrics(this.info, asc, desc, gap);
               this.ascent = asc.get(0) * this.scale;
               this.descent = -desc.get(0) * this.scale;
            } catch (Throwable var8) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var6) {
                     var8.addSuppressed(var6);
                  }
               }

               throw var8;
            }

            if (stack != null) {
               stack.close();
            }

            this.buildAtlas(pixelScale);
         }
      }
   }

   private void buildAtlas(float pixelScale) {
      ByteBuffer atlas = MemoryUtil.memCalloc(1048576);
      int penX = 1;
      int penY = 1;
      int rowH = 0;
      MemoryStack stack = MemoryStack.stackPush();

      try {
         IntBuffer w = stack.mallocInt(1);
         IntBuffer h = stack.mallocInt(1);
         IntBuffer xo = stack.mallocInt(1);
         IntBuffer yo = stack.mallocInt(1);
         IntBuffer adv = stack.mallocInt(1);
         IntBuffer lsb = stack.mallocInt(1);
         int i = 0;

         while (i < this.chars.length()) {
            int cp = this.chars.codePointAt(i);
            i += Character.charCount(cp);
            int index = STBTruetype.stbtt_FindGlyphIndex(this.info, cp);
            if (index != 0) {
               STBTruetype.stbtt_GetGlyphHMetrics(this.info, index, adv, lsb);
               float advance = adv.get(0) * this.scale;
               // onedge 128 = Kante bei 0.5; hoeherer Wert -> Shader-Schwelle frueher erreicht
               // -> dickerer Strich (Pseudo-Bold fuer BOLD)
               ByteBuffer sdf = STBTruetype.stbtt_GetGlyphSDF(this.info, pixelScale, index, 6, (byte)(128 + this.boldBias), 21.333334F, w, h, xo, yo);
               if (sdf == null) {
                  this.glyphs.put(cp, new ZeroFont.Glyph(advance, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, index));
               } else {
                  int gw = w.get(0);
                  int gh = h.get(0);
                  if (penX + gw + 1 >= 1024) {
                     penX = 1;
                     penY += rowH + 1;
                     rowH = 0;
                  }

                  if (penY + gh + 1 >= 1024) {
                     STBTruetype.stbtt_FreeSDF(sdf);
                     throw new IllegalStateException("Font-Atlas zu klein: " + this.file);
                  }

                  for (int y = 0; y < gh; y++) {
                     for (int x = 0; x < gw; x++) {
                        atlas.put((penY + y) * 1024 + penX + x, sdf.get(y * gw + x));
                     }
                  }

                  STBTruetype.stbtt_FreeSDF(sdf);
                  float x0 = xo.get(0) / 42.0F;
                  float y0 = yo.get(0) / 42.0F;
                  this.glyphs
                     .put(
                        cp,
                        new ZeroFont.Glyph(
                           advance,
                           x0,
                           y0,
                           x0 + gw / 42.0F,
                           y0 + gh / 42.0F,
                           penX / 1024.0F,
                           penY / 1024.0F,
                           (penX + gw) / 1024.0F,
                           (penY + gh) / 1024.0F,
                           index
                        )
                     );
                  penX += gw + 1;
                  rowH = Math.max(rowH, gh);
               }
            }
         }
      } catch (Throwable var23) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var22) {
               var23.addSuppressed(var22);
            }
         }

         throw var23;
      }

      if (stack != null) {
         stack.close();
      }

      this.upload(atlas);
      MemoryUtil.memFree(atlas);
   }

   private void upload(ByteBuffer atlas) {
      GpuDevice device = RenderSystem.getDevice();
      CommandEncoder encoder = device.createCommandEncoder();
      this.texture = device.createTexture("ZERO font " + this.file, 5, TextureFormat.RED8, 1024, 1024, 1, 3);
      this.view = device.createTextureView(this.texture);
      ByteBuffer level = atlas;
      int size = 1024;

      for (int mip = 0; mip < 3; mip++) {
         encoder.writeToTexture(this.texture, level, Format.LUMINANCE, mip, 0, 0, 0, size, size);
         if (mip + 1 == 3) {
            break;
         }

         int half = size / 2;
         ByteBuffer next = MemoryUtil.memAlloc(half * half);

         for (int y = 0; y < half; y++) {
            for (int x = 0; x < half; x++) {
               int sum = (level.get(y * 2 * size + x * 2) & 255)
                  + (level.get(y * 2 * size + x * 2 + 1) & 255)
                  + (level.get((y * 2 + 1) * size + x * 2) & 255)
                  + (level.get((y * 2 + 1) * size + x * 2 + 1) & 255);
               next.put(y * half + x, (byte)(sum / 4));
            }
         }

         if (level != atlas) {
            MemoryUtil.memFree(level);
         }

         level = next;
         size = half;
      }

      if (level != atlas) {
         MemoryUtil.memFree(level);
      }
   }

   private static String ascii() {
      StringBuilder b = new StringBuilder();

      for (char c = ' '; c < 127; c++) {
         b.append(c);
      }

      return b.toString();
   }

   private static String latin() {
      StringBuilder b = new StringBuilder(ascii());

      for (char c = 161; c <= 255; c++) {
         b.append(c);
      }

      return b.append("…–—•").toString();
   }

   record Glyph(float advance, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int index) {
   }

   public static final class Icon {
      public static final char SWORDS = '\ue2b4';
      public static final char FOOTPRINTS = '\ue3bd';
      public static final char EYE = '\ue0be';
      public static final char USER = '\ue19f';
      public static final char GLOBE = '\ue0eb';
      public static final char ELLIPSIS = '\ue0ba';
      public static final char SETTINGS = '\ue158';
      public static final char SAVE = '\ue151';
      public static final char KEYBOARD = '\ue284';
      public static final char SEARCH = '\ue155';
      public static final char LIST = '\ue1d9';
      public static final char GRID = '\ue105';
      public static final char CHEVRON_DOWN = '\ue071';
      public static final char CHEVRON_RIGHT = '\ue073';
      public static final char MAP_PIN = '\ue115';
      public static final char RESET = '\ue14c';
      public static final char TRASH = '\ue18e';
      public static final char X = '\ue1b2';
      public static final char PLUS = '\ue141';
      public static final char CHECK = '\ue070';
      public static final char UPLOAD = '\ue19e';
      public static final char MONITOR = '\ue121';
      public static final char CLOUD = '\ue08c';
      public static final char CLOCK = '\ue08b';
      public static final char MUSIC = '\ue126';
      public static final char FLASK = '\ue0da';
      public static final char GAUGE = '\ue1bf';
      public static final char NAVIGATION = '\ue127';
      public static final char SKIP_BACK = '\ue163';
      public static final char SKIP_FORWARD = '\ue164';
      public static final char PLAY = '\ue140';
      public static final char PAUSE = '\ue132';
      public static final char HEART = '\ue0f5';
      public static final char SHIELD = '\ue15c';
      public static final char ACTIVITY = '\ue038';
      public static final char TIMER = '\ue1e0';
      public static final char SPARKLES = '\ue417';
      public static final char DROPLET = '\ue0b8';
      public static final char FLAME = '\ue0d7';
      public static final char PALETTE = '\ue1dd';
      public static final char BELL = '\ue05d';
      public static final char LAYERS = '\ue104';
      public static final char IMAGE = '\ue0f9';
      public static final char CROSSHAIR = '\ue0b0';
      public static final char ZAP = '\ue1b4';
      public static final char DRUMSTICK = '\ue25b';
      public static final char USERS = '\ue1a4';
      public static final char WIFI = '\ue1ae';
      public static final char STAR = '\ue17a';
      public static final char MOON = '\ue122';
      public static final char SUN = '\ue17c';
      public static final char BOX = '\ue065';
      public static final char SLIDERS = '\ue29a';
      public static final char TOGGLE = '\ue18c';
      public static final char COMMAND = '\ue09e';
      public static final char COMPASS = '\ue09f';
      public static final char CPU = '\ue0ad';
      public static final char SIGNAL = '\ue25f';
      public static final char SWATCH = '\ue5a4';
      public static final char HOUSE = '\ue0f8';
      public static final char CIRCLE_USER = '\ue466';
      public static final char PACKAGE = '\ue12d';
      public static final char SETTINGS2 = '\ue245';
      public static final char BRUSH = '\ue2e7';
      public static final char WAND = '\ue35b';
      public static final char USER_PLUS = '\ue1a2';
      public static final char DOWNLOAD = '\ue0b6';
      public static final char COPY = '\ue0a2';
      public static final char LINK = '\ue108';
      public static final char ARROW_LEFT = '\ue04c';
      public static final char INFO = '\ue0fe';
      public static final char POWER = '\ue144';
      public static final char CHEVRON_LEFT = '\ue072';
      public static final char CHEVRON_UP = '\ue074';
      static final String ALL = "\ue038\ue04c\ue05d\ue065\ue070\ue071\ue072\ue073\ue074\ue08b\ue08c\ue09e\ue09f\ue0a2\ue0ad\ue0b0\ue0b6\ue0b8\ue0ba\ue0be\ue0d7\ue0da\ue0eb\ue0f5\ue0f8\ue0f9\ue0fe\ue104\ue105\ue108\ue10c\ue115\ue121\ue122\ue126\ue127\ue12d\ue132\ue140\ue141\ue144\ue14c\ue151\ue155\ue158\ue15c\ue163\ue164\ue17a\ue17c\ue18c\ue18e\ue19e\ue19f\ue1a2\ue1a4\ue1ae\ue1b2\ue1b4\ue1bf\ue1d9\ue1dd\ue1e0\ue245\ue25b\ue25f\ue284\ue29a\ue2b4\ue2e7\ue35b\ue3bd\ue417\ue466\ue504\ue5a4?";

      private Icon() {
      }
   }
}
