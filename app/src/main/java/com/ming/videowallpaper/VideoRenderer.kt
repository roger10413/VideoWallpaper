package com.ming.videowallpaper

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch

/**
 * Draws decoded video frames onto the wallpaper surface with OpenGL ES, so the frame can be
 * scaled to fill (crop) or fit (letterbox) the screen. The player decodes into [inputSurface];
 * every new frame is drawn to whatever output surface is currently attached.
 *
 * All GL work happens on a dedicated thread. Public methods may be called from any thread.
 */
class VideoRenderer {
    private val thread = HandlerThread("VideoRenderer").apply { start() }
    private val handler = Handler(thread.looper)

    // Everything below is only touched on the GL thread.
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer = EGL14.EGL_NO_SURFACE
    private var window = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var textureId = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private var uScale = 0
    private lateinit var surfaceTexture: SurfaceTexture
    private val texMatrix = FloatArray(16)
    private var hasFrame = false
    private var videoWidth = 0
    private var videoHeight = 0
    private var scaleMode = ScaleMode.FILL

    /** Surface for the player to decode into. */
    val inputSurface: Surface = runOnGlAndWait { initGl() }

    /** Starts drawing to [surface] (the wallpaper's surface). */
    fun setOutput(surface: Surface) = handler.post {
        releaseWindow()
        window = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (window == EGL14.EGL_NO_SURFACE) {
            Log.w(TAG, "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            return@post
        }
        EGL14.eglMakeCurrent(display, window, window, context)
        draw()
    }

    /** Stops drawing to the output surface. Blocks until done, as SurfaceHolder requires. */
    fun clearOutput() = runOnGlAndWait { releaseWindow() }

    fun requestRender() = handler.post { draw() }

    fun setVideoSize(width: Int, height: Int) = handler.post {
        videoWidth = width
        videoHeight = height
        draw()
    }

    fun setScaleMode(mode: ScaleMode) = handler.post {
        scaleMode = mode
        draw()
    }

    /** Shows plain black until the next frame arrives (e.g. when the playlist is emptied). */
    fun clearFrame() = handler.post {
        hasFrame = false
        draw()
    }

    /** Call after the player has stopped using [inputSurface]. */
    fun release() {
        handler.post {
            releaseWindow()
            surfaceTexture.release()
            inputSurface.release()
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, pbuffer)
            EGL14.eglDestroyContext(display, context)
            // No eglTerminate: the default display is shared with other wallpaper engines in this process.
            EGL14.eglReleaseThread()
        }
        thread.quitSafely()
    }

    private fun initGl(): Surface {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
            "eglChooseConfig failed"
        }
        config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        // A 1x1 pbuffer keeps the context current while no wallpaper surface exists, so incoming
        // frames can still be latched with updateTexImage().
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)

        program = buildProgram()
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uScale = GLES20.glGetUniformLocation(program, "uScale")

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setOnFrameAvailableListener({ onFrameAvailable() }, handler)
        return Surface(surfaceTexture)
    }

    private fun onFrameAvailable() {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(texMatrix)
        hasFrame = true
        draw()
    }

    private fun draw() {
        if (window == EGL14.EGL_NO_SURFACE) return
        val size = IntArray(2)
        EGL14.eglQuerySurface(display, window, EGL14.EGL_WIDTH, size, 0)
        EGL14.eglQuerySurface(display, window, EGL14.EGL_HEIGHT, size, 1)
        val (viewWidth, viewHeight) = size
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (hasFrame && videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            // ratio > 1: the video is wider than the screen.
            val ratio = (videoWidth.toFloat() / videoHeight) / (viewWidth.toFloat() / viewHeight)
            var scaleX = 1f
            var scaleY = 1f
            when (scaleMode) {
                ScaleMode.FILL -> if (ratio > 1f) scaleX = ratio else scaleY = 1f / ratio
                ScaleMode.FIT -> if (ratio > 1f) scaleY = 1f / ratio else scaleX = ratio
            }
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
            GLES20.glUniform2f(uScale, scaleX, scaleY)
            GLES20.glEnableVertexAttribArray(aPosition)
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, QUAD_POSITIONS)
            GLES20.glEnableVertexAttribArray(aTexCoord)
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, QUAD_TEX_COORDS)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        EGL14.eglSwapBuffers(display, window)
    }

    private fun releaseWindow() {
        if (window == EGL14.EGL_NO_SURFACE) return
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        EGL14.eglDestroySurface(display, window)
        window = EGL14.EGL_NO_SURFACE
    }

    private fun <T> runOnGlAndWait(block: () -> T): T {
        if (Looper.myLooper() == thread.looper) return block()
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        check(handler.post { result = runCatching(block); latch.countDown() }) { "Renderer already released" }
        latch.await()
        return result!!.getOrThrow()
    }

    private fun buildProgram(): Int {
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER))
        GLES20.glAttachShader(program, compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private companion object {
        const val TAG = "VideoRenderer"

        val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            uniform vec2 uScale;
            attribute vec2 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition * uScale, 0.0, 1.0);
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """.trimIndent()

        // The #extension line must come first in the shader source.
        val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """.trimIndent()

        val QUAD_POSITIONS = floatBuffer(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val QUAD_TEX_COORDS = floatBuffer(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

        fun floatBuffer(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                .put(values).apply { position(0) }
    }
}
