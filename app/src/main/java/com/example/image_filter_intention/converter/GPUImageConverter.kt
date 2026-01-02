package com.example.image_filter_intention.converter

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.EGLSurface
import android.opengl.GLES20
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import androidx.core.graphics.createBitmap

/**
 * GPU YUV->RGBA converter (offscreen GL). Falls back to CPU if GL init fails.
 * Note: For production, prefer sharing textures with your render path and avoid readPixels.
 */
// TODO: FIX IT. DOESNT WORK
class GPUImageConverter() : IImageConverter {

    override fun yuvToRgba(image: ImageProxy): RgbaBuffer? {
        val w = image.width
        val h = image.height
        val egl = EGLEnv(w, h)
        if (!egl.ready) {
            egl.release()
            return null
        }
        val prog = GlProgram(VS, FS)
        if (!prog.ready) {
            prog.release()
            egl.release()
            return null
        }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val tex = IntArray(3)
        GLES20.glGenTextures(3, tex, 0)
        uploadLuma(tex[0], w, h, yPlane.buffer, yPlane.rowStride, yPlane.pixelStride)
        uploadLuma(tex[1], w / 2, h / 2, uPlane.buffer, uPlane.rowStride, uPlane.pixelStride)
        uploadLuma(tex[2], w / 2, h / 2, vPlane.buffer, vPlane.rowStride, vPlane.pixelStride)

        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(prog.id)
        GLES20.glEnableVertexAttribArray(prog.aPos)
        GLES20.glVertexAttribPointer(prog.aPos, 2, GLES20.GL_FLOAT, false, 0, QUAD_BUF)
        GLES20.glEnableVertexAttribArray(prog.aTex)
        GLES20.glVertexAttribPointer(prog.aTex, 2, GLES20.GL_FLOAT, false, 0, UV_BUF)

        bindTex(0, tex[0], prog.uY)
        bindTex(1, tex[1], prog.uU)
        bindTex(2, tex[2], prog.uV)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        val outBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, outBuf)

        GLES20.glDeleteTextures(3, tex, 0)
        prog.release()
        egl.release()

        val bytes = ByteArray(outBuf.remaining())
        outBuf.get(bytes)
        return RgbaBuffer(bytes, w, h)
    }

    override fun rgbaToBitmap(buffer: RgbaBuffer): Bitmap {
        // Convert RGBA byte buffer to a Bitmap (GPU path renders offscreen, readPixels already done).
        val outBuffer = ByteBuffer.wrap(buffer.bytes)
        return createBitmap(buffer.width, buffer.height).apply {
            copyPixelsFromBuffer(outBuffer)
        }
    }

    private fun uploadLuma(texId: Int, w: Int, h: Int, buf: ByteBuffer, rowStride: Int, pixelStride: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val tight = ByteArray(w * h)
        var offset = 0
        for (row in 0 until h) {
            val rowPos = row * rowStride
            if (pixelStride == 1) {
                buf.position(rowPos)
                buf.get(tight, offset, w)
            } else {
                for (col in 0 until w) {
                    tight[offset + col] = buf.get(rowPos + col * pixelStride)
                }
            }
            offset += w
        }
        val tightBuf = ByteBuffer.wrap(tight)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
            w, h, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, tightBuf
        )
    }

    private fun bindTex(unit: Int, texId: Int, handle: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(handle, unit)
    }

    private class GlProgram(vs: String, fs: String) {
        val id: Int
        val aPos: Int
        val aTex: Int
        val uY: Int
        val uU: Int
        val uV: Int
        val ready: Boolean

        init {
            val vsId = compile(GLES20.GL_VERTEX_SHADER, vs)
            val fsId = compile(GLES20.GL_FRAGMENT_SHADER, fs)
            id = GLES20.glCreateProgram()
            GLES20.glAttachShader(id, vsId)
            GLES20.glAttachShader(id, fsId)
            GLES20.glLinkProgram(id)
            val st = IntArray(1)
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, st, 0)
            ready = st[0] == GLES20.GL_TRUE
            aPos = GLES20.glGetAttribLocation(id, "aPosition")
            aTex = GLES20.glGetAttribLocation(id, "aTexCoord")
            uY = GLES20.glGetUniformLocation(id, "yTex")
            uU = GLES20.glGetUniformLocation(id, "uTex")
            uV = GLES20.glGetUniformLocation(id, "vTex")
        }

        fun release() {
            GLES20.glDeleteProgram(id)
        }

        private fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            return shader
        }
    }

    private class EGLEnv(w: Int, h: Int) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ready: Boolean
        private val ctx: EGLContext?
        private val surf: EGLSurface?

        init {
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)
            val attrib = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(display, attrib, 0, configs, 0, 1, num, 0)
            val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            ctx = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
            val surfAttrib = intArrayOf(
                EGL14.EGL_WIDTH, w,
                EGL14.EGL_HEIGHT, h,
                EGL14.EGL_NONE
            )
            surf = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttrib, 0)
            EGL14.eglMakeCurrent(display, surf, surf, ctx)
            ready = ctx != null && surf != null
        }

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surf != null) EGL14.eglDestroySurface(display, surf)
            if (ctx != null) EGL14.eglDestroyContext(display, ctx)
            EGL14.eglTerminate(display)
        }
    }

    companion object {
        private val QUAD = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )
        private val UV = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )
        private val QUAD_BUF: FloatBuffer = ByteBuffer.allocateDirect(QUAD.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(QUAD).position(0) }
        private val UV_BUF: FloatBuffer = ByteBuffer.allocateDirect(UV.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(UV).position(0) }

        private const val VS = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = aPosition;
            }
        """

        private const val FS = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D yTex;
            uniform sampler2D uTex;
            uniform sampler2D vTex;
            void main() {
                float y = texture2D(yTex, vTexCoord).r;
                float u = texture2D(uTex, vTexCoord).r - 0.5;
                float v = texture2D(vTex, vTexCoord).r - 0.5;
                float r = y + 1.370705 * v;
                float g = y - 0.337633 * u - 0.698001 * v;
                float b = y + 1.732446 * u;
                gl_FragColor = vec4(r, g, b, 1.0);
            }
        """
    }
}

