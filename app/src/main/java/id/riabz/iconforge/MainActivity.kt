package id.riabz.iconforge

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private enum class Screen { EDITOR, APPS, EXPORT }

    private data class AppTarget(
        val label: String,
        val packageName: String,
        val activityName: String,
        val icon: android.graphics.drawable.Drawable? = null
    ) {
        val componentKey: String get() = "$packageName/$activityName"
    }

    private data class IconAssignment(val target: AppTarget, val bitmap: Bitmap?, val fileName: String)

    companion object {
        private const val REQUEST_IMAGE = 100
        private const val REQUEST_PNG = 101
        private const val REQUEST_PACK_ZIP = 102
        private const val ICON_SIZE = 512
        private const val DEFAULT_COLOR = 0xFF6C63FF.toInt()
        private const val BACKGROUND = 0xFF0B1220.toInt()
        private const val SURFACE = 0xFF141E31.toInt()
        private const val MUTED = 0xFF9AA9C2.toInt()
        private const val TEXT = 0xFFF3F6FC.toInt()
        private val PALETTE = intArrayOf(
            0xFF6C63FF.toInt(), 0xFF31C48D.toInt(), 0xFFFF6B81.toInt(),
            0xFFFFB84D.toInt(), 0xFF43B5E8.toInt(), 0xFF1E293B.toInt(),
            0xFFF1F5F9.toInt(), 0xFF111827.toInt(), 0xFFB794F4.toInt()
        )
    }

    private lateinit var root: LinearLayout
    private lateinit var page: LinearLayout
    private lateinit var navEditor: TextView
    private lateinit var navApps: TextView
    private lateinit var navExport: TextView
    private var previewImage: ImageView? = null
    private var screen: Screen = Screen.EDITOR

    private var shapeName: String = "Rounded square"
    private var shapeColor: Int = DEFAULT_COLOR
    private var imageScale: Float = 1f
    private var imageRotation: Float = 0f
    private var customBitmap: Bitmap? = null
    private var assignments = linkedMapOf<String, IconAssignment>()
    private val installedApps = mutableListOf<AppTarget>()
    private var pendingBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BACKGROUND
        window.navigationBarColor = BACKGROUND
        window.decorView.systemUiVisibility = 0
        loadEditorState()
        loadAssignments()
        resolveLauncherActivities()
        buildShell()
        showScreen(Screen.EDITOR)
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            clipToPadding = false
        }
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(15), dp(20), dp(14))
            setBackgroundColor(0xFF0F192B.toInt())
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "◈"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFF6C63FF.toInt(), 14, 0xFF6C63FF.toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        }
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandText.addView(label("ICONFORGE", 19f, TEXT, true).apply { letterSpacing = 0.08f })
        brandText.addView(label("Studio ikon & icon pack launcher", 12f, MUTED))
        brandRow.addView(mark)
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        header.addView(brandRow)
        header.addView(label("Buat ikon transparan, pasangkan ke aplikasi, lalu ekspor paket.", 13f, MUTED).apply {
            setPadding(0, dp(12), 0, 0)
        })
        root.addView(header)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xFF0F192B.toInt())
        }
        navEditor = navButton("Editor") { showScreen(Screen.EDITOR) }
        navApps = navButton("Pemetaan") { showScreen(Screen.APPS) }
        navExport = navButton("Ekspor") { showScreen(Screen.EXPORT) }
        nav.addView(navEditor, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        nav.addView(navApps, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
        nav.addView(navExport, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
        root.addView(nav)

        val scroll = ScrollView(this).apply {
            fillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = true
            setPadding(0, 0, 0, dp(12))
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(20))
        }
        scroll.addView(page, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showScreen(next: Screen) {
        screen = next
        styleNav(navEditor, next == Screen.EDITOR)
        styleNav(navApps, next == Screen.APPS)
        styleNav(navExport, next == Screen.EXPORT)
        page.removeAllViews()
        previewImage = null
        when (next) {
            Screen.EDITOR -> buildEditorPage()
            Screen.APPS -> buildAppsPage()
            Screen.EXPORT -> buildExportPage()
        }
    }

    private fun buildEditorPage() {
        page.addView(label("EDITOR IKON", 18f, TEXT, true))
        page.addView(label("Kanvas 512 × 512 px • PNG transparan", 13f, MUTED).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        val previewCard = card()
        previewCard.addView(label("Pratinjau", 14f, TEXT, true))
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(232)).apply { topMargin = dp(8) }
        }
        val checker = CheckerboardView()
        val checkerParams = FrameLayout.LayoutParams(dp(208), dp(208), Gravity.CENTER)
        frame.addView(checker, checkerParams)
        previewImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(renderIcon())
        }
        frame.addView(previewImage, FrameLayout.LayoutParams(dp(208), dp(208), Gravity.CENTER))
        previewCard.addView(frame)
        previewCard.addView(label("Pola kotak hanya penanda transparansi dan tidak ikut tersimpan.", 11f, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        })
        page.addView(previewCard)

        val shapeCard = card()
        shapeCard.addView(label("1. Bentuk template", 15f, TEXT, true))
        shapeCard.addView(label("Pilih siluet latar ikon.", 12f, MUTED).apply { setPadding(0, dp(3), 0, dp(10)) })
        val shapes = listOf("Circle", "Rounded square", "Squircle", "Hexagon", "Diamond")
        val shapeScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val shapeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shapes.forEach { shape ->
            val chip = smallButton(shape, selected = shape == shapeName) {
                shapeName = shape
                persistEditorState()
                showScreen(Screen.EDITOR)
            }
            shapeRow.addView(chip, LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(7) })
        }
        shapeScroll.addView(shapeRow)
        shapeCard.addView(shapeScroll)
        page.addView(shapeCard)

        val colorCard = card()
        colorCard.addView(label("2. Warna template", 15f, TEXT, true))
        colorCard.addView(label("Latar di luar bentuk tetap transparan.", 12f, MUTED).apply {
            setPadding(0, dp(3), 0, dp(10))
        })
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        PALETTE.forEach { color ->
            val swatch = TextView(this).apply {
                text = if (color == shapeColor) "✓" else ""
                gravity = Gravity.CENTER
                setTextColor(if (color == Color.WHITE || color == 0xFFF1F5F9.toInt()) Color.BLACK else Color.WHITE)
                background = rounded(color, 50, if (color == shapeColor) Color.WHITE else 0xFF33415B.toInt(), if (color == shapeColor) 2 else 1)
                layoutParams = LinearLayout.LayoutParams(dp(31), dp(31)).apply { marginEnd = dp(8) }
                setOnClickListener {
                    shapeColor = color
                    persistEditorState()
                    showScreen(Screen.EDITOR)
                }
            }
            colorRow.addView(swatch)
        }
        colorCard.addView(colorRow)
        colorCard.addView(label("Warna aktif: ${String.format(Locale.US, "#%06X", 0xFFFFFF and shapeColor)}", 12f, MUTED).apply {
            setPadding(0, dp(9), 0, 0)
        })
        page.addView(colorCard)

        val imageCard = card()
        imageCard.addView(label("3. Gambar custom", 15f, TEXT, true))
        imageCard.addView(label("Tambahkan gambar dari perangkat. Gambar dipotong mengikuti bentuk template.", 12f, MUTED).apply {
            setPadding(0, dp(3), 0, dp(10))
        })
        val imageButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        imageButtons.addView(actionButton(if (customBitmap == null) "Pilih gambar" else "Ganti gambar", true) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_IMAGE)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        imageButtons.addView(actionButton("Hapus gambar", false) {
            customBitmap = null
            File(filesDir, "custom_overlay.png").delete()
            imageScale = 1f
            imageRotation = 0f
            persistEditorState()
            refreshPreview()
            showScreen(Screen.EDITOR)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
        imageCard.addView(imageButtons)
        imageCard.addView(label(if (customBitmap == null) "Belum ada gambar tambahan." else "Gambar custom aktif.", 12f, MUTED).apply {
            setPadding(0, dp(9), 0, dp(3))
        })
        page.addView(imageCard)

        val transformCard = card()
        transformCard.addView(label("4. Ukuran & rotasi gambar", 15f, TEXT, true))
        val scaleValue = label("Ukuran: ${(imageScale * 100).toInt()}%", 13f, TEXT, true).apply { setPadding(0, dp(10), 0, 0) }
        transformCard.addView(scaleValue)
        transformCard.addView(SeekBar(this).apply {
            max = 115
            progress = ((imageScale - 0.25f) * 100f).toInt().coerceIn(0, 115)
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF8B83FF.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFB8B3FF.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    imageScale = (25 + progress).coerceIn(25, 140) / 100f
                    scaleValue.text = "Ukuran: ${(imageScale * 100).toInt()}%"
                    persistEditorState()
                    refreshPreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        val rotationValue = label("Rotasi: ${imageRotation.toInt()}°", 13f, TEXT, true).apply { setPadding(0, dp(5), 0, 0) }
        transformCard.addView(rotationValue)
        transformCard.addView(SeekBar(this).apply {
            max = 360
            progress = imageRotation.toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF8B83FF.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFB8B3FF.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    imageRotation = progress.toFloat()
                    rotationValue.text = "Rotasi: ${progress}°"
                    persistEditorState()
                    refreshPreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        page.addView(transformCard)

        page.addView(actionButton("Lanjut ke pemetaan aplikasi  →", true) { showScreen(Screen.APPS) }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) }
        })
    }

    private fun buildAppsPage() {
        page.addView(label("PEMETAAN APLIKASI", 18f, TEXT, true))
        page.addView(label("Pilih aplikasi terpasang. Nama paket dan launcher activity dideteksi dari Android.", 13f, MUTED).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        val info = card()
        info.addView(label("${installedApps.size} aktivitas launcher terdeteksi", 15f, TEXT, true))
        info.addView(label("${assignments.size} pemetaan ikon tersimpan di perangkat", 12f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
        info.addView(label("Tombol ‘Pasangkan ikon editor’ memakai desain yang sedang ada di tab Editor.", 12f, MUTED).apply {
            setPadding(0, dp(8), 0, 0)
        })
        page.addView(info)

        val search = EditText(this).apply {
            hint = "Cari nama aplikasi atau package name…"
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            textSize = 14f
            singleLine = true
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(SURFACE, 13, 0xFF2B3952.toInt(), 1)
        }
        page.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10); bottomMargin = dp(10) })
        val listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(listHolder)
        fun populate(queryRaw: String) {
            listHolder.removeAllViews()
            val query = queryRaw.trim().lowercase(Locale.ROOT)
            val filtered = installedApps.filter {
                query.isEmpty() || it.label.lowercase(Locale.ROOT).contains(query) ||
                    it.packageName.lowercase(Locale.ROOT).contains(query) ||
                    it.activityName.lowercase(Locale.ROOT).contains(query)
            }
            if (filtered.isEmpty()) {
                val empty = card()
                empty.addView(label("Tidak ada aplikasi yang cocok.", 14f, MUTED))
                listHolder.addView(empty)
                return
            }
            filtered.take(300).forEach { target ->
                val row = card()
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val appIcon = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    target.icon?.let { setImageDrawable(it) }
                }
                top.addView(appIcon, LinearLayout.LayoutParams(dp(38), dp(38)))
                val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                titleBlock.addView(label(target.label, 14f, TEXT, true))
                titleBlock.addView(label(target.packageName, 11f, MUTED).apply { setPadding(0, dp(2), 0, 0) })
                top.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
                val mapped = assignments.containsKey(target.componentKey)
                val badge = TextView(this).apply {
                    text = if (mapped) "✓" else ""
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    background = rounded(if (mapped) 0xFF217B61.toInt() else 0xFF2A3549.toInt(), 40, 0, 0)
                }
                top.addView(badge, LinearLayout.LayoutParams(dp(28), dp(28)))
                row.addView(top)
                row.addView(label("Activity: ${target.activityName}", 10f, MUTED).apply {
                    setPadding(0, dp(8), 0, dp(8))
                })
                row.addView(actionButton(if (mapped) "Perbarui ikon" else "Pasangkan ikon editor", true) {
                    assignments[target.componentKey] = IconAssignment(target, renderIcon(), stableFileName(target.componentKey) + ".png")
                    saveAssignments()
                    Toast.makeText(this, "Ikon dipasangkan ke ${target.label}", Toast.LENGTH_SHORT).show()
                    populate(search.text.toString())
                }, LinearLayout.LayoutParams(-1, dp(40)))
                listHolder.addView(row)
            }
            if (filtered.size > 300) {
                listHolder.addView(label("Menampilkan 300 dari ${filtered.size} hasil. Persempit pencarian untuk menemukan aplikasi lain.", 12f, MUTED).apply {
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                })
            }
        }
        search.addTextChangedListener(SimpleTextWatcher { populate(it) })
        populate("")
        page.addView(actionButton("Lihat ekspor paket  →", true) { showScreen(Screen.EXPORT) }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) }
        })
    }

    private fun buildExportPage() {
        page.addView(label("EKSPOR", 18f, TEXT, true))
        page.addView(label("Simpan PNG transparan atau buat proyek icon pack Android.", 13f, MUTED).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        val pngCard = card()
        pngCard.addView(label("PNG ikon", 16f, TEXT, true))
        pngCard.addView(label("Resolusi 512 × 512 px, RGBA, area di luar bentuk transparan. Bisa dipakai sebagai aset ikon atau diedit lagi.", 12f, MUTED).apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        pngCard.addView(actionButton("Simpan PNG ke perangkat", true) {
            pendingBitmap = renderIcon()
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/png"
                putExtra(Intent.EXTRA_TITLE, "iconforge-icon.png")
            }
            startActivityForResult(intent, REQUEST_PNG)
        }, LinearLayout.LayoutParams(-1, dp(46)))
        page.addView(pngCard)

        val packCard = card()
        packCard.addView(label("Proyek icon pack Android", 16f, TEXT, true))
        packCard.addView(label("${assignments.size} aplikasi dipetakan", 14f, if (assignments.isEmpty()) 0xFFFFC36B.toInt() else 0xFF58D6A5.toInt(), true).apply {
            setPadding(0, dp(7), 0, dp(4))
        })
        packCard.addView(label("ZIP berisi proyek Android Studio, PNG ikon, res/xml/appfilter.xml, dan drawable.xml. Bangun proyek menjadi APK lalu pilih lewat pengaturan icon pack pada launcher yang mendukungnya.", 12f, MUTED).apply {
            setPadding(0, dp(3), 0, dp(12))
        })
        if (assignments.isEmpty()) {
            packCard.addView(label("Belum ada pemetaan. Buka tab Pemetaan dan pasangkan desain ke setidaknya satu aplikasi.", 12f, 0xFFFFC36B.toInt()).apply {
                setPadding(0, 0, 0, dp(10))
            })
        }
        packCard.addView(actionButton("Ekspor proyek icon pack (.zip)", primary = true, enabled = assignments.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "IconForge-Launcher-Pack.zip")
            }
            startActivityForResult(intent, REQUEST_PACK_ZIP)
        }, LinearLayout.LayoutParams(-1, dp(46)))
        page.addView(packCard)

        val note = card()
        note.addView(label("Catatan kompatibilitas", 15f, TEXT, true))
        note.addView(label("Icon pack tidak mengganti file ikon aplikasi asli. Launcher pihak ketiga yang kompatibel membaca pemetaan komponen untuk menampilkan ikon pengganti. Dukungan dan langkah penerapan berbeda antar-launcher; ZIP ini merupakan proyek sumber untuk dibangun menjadi APK, bukan icon pack yang langsung dipasang.", 12f, MUTED).apply {
            setPadding(0, dp(7), 0, 0)
        })
        page.addView(note)
    }

    @Suppress("DEPRECATION")
    private fun resolveLauncherActivities() {
        installedApps.clear()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results: List<ResolveInfo> = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        val seen = hashSetOf<String>()
        results.forEach { info ->
            val activity = info.activityInfo ?: return@forEach
            val pkg = activity.packageName ?: return@forEach
            val cls = activity.name ?: return@forEach
            val key = "$pkg/$cls"
            if (!seen.add(key) || pkg == packageName) return@forEach
            val labelText = try { info.loadLabel(packageManager)?.toString() ?: pkg } catch (_: Exception) { pkg }
            val icon = try { info.loadIcon(packageManager) } catch (_: Exception) { null }
            installedApps.add(AppTarget(labelText, pkg, cls, icon))
        }
        installedApps.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun renderIcon(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = makeShapePath(shapeName)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shapeColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fill)

        customBitmap?.let { source ->
            val save = canvas.save()
            canvas.clipPath(path)
            canvas.translate(ICON_SIZE / 2f, ICON_SIZE / 2f)
            canvas.rotate(imageRotation)
            val baseSize = 376f * imageScale
            val ratio = minOf(baseSize / source.width.toFloat(), baseSize / source.height.toFloat())
            val drawWidth = source.width * ratio
            val drawHeight = source.height * ratio
            val destination = RectF(-drawWidth / 2f, -drawHeight / 2f, drawWidth / 2f, drawHeight / 2f)
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawBitmap(source, null, destination, bitmapPaint)
            canvas.restoreToCount(save)
        }
        return bitmap
    }

    private fun makeShapePath(name: String): Path {
        val path = Path()
        val bounds = RectF(30f, 30f, 482f, 482f)
        when (name) {
            "Circle" -> path.addCircle(256f, 256f, 226f, Path.Direction.CW)
            "Rounded square" -> path.addRoundRect(bounds, 90f, 90f, Path.Direction.CW)
            "Squircle" -> path.addRoundRect(bounds, 135f, 135f, Path.Direction.CW)
            "Hexagon" -> {
                path.moveTo(145f, 34f); path.lineTo(367f, 34f); path.lineTo(478f, 256f)
                path.lineTo(367f, 478f); path.lineTo(145f, 478f); path.lineTo(34f, 256f); path.close()
            }
            "Diamond" -> {
                path.moveTo(256f, 25f); path.lineTo(487f, 256f); path.lineTo(256f, 487f)
                path.lineTo(25f, 256f); path.close()
            }
            else -> path.addRoundRect(bounds, 90f, 90f, Path.Direction.CW)
        }
        return path
    }

    private fun refreshPreview() {
        previewImage?.setImageBitmap(renderIcon())
    }

    private fun loadEditorState() {
        val prefs = getSharedPreferences("iconforge", MODE_PRIVATE)
        shapeName = prefs.getString("shape", "Rounded square") ?: "Rounded square"
        shapeColor = prefs.getInt("color", DEFAULT_COLOR)
        imageScale = prefs.getFloat("scale", 1f).coerceIn(0.25f, 1.4f)
        imageRotation = prefs.getFloat("rotation", 0f).coerceIn(0f, 360f)
        val file = File(filesDir, "custom_overlay.png")
        customBitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    private fun persistEditorState() {
        getSharedPreferences("iconforge", MODE_PRIVATE).edit()
            .putString("shape", shapeName)
            .putInt("color", shapeColor)
            .putFloat("scale", imageScale)
            .putFloat("rotation", imageRotation)
            .apply()
    }

    private fun saveCustomBitmap(bitmap: Bitmap?) {
        val file = File(filesDir, "custom_overlay.png")
        if (bitmap == null) {
            file.delete()
            return
        }
        try { FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Exception) { }
    }

    private fun loadAssignments() {
        val dir = File(filesDir, "assigned_icons")
        val manifest = File(filesDir, "assignments.tsv")
        if (!manifest.exists()) return
        try {
            manifest.readLines().forEach { line ->
                val fields = line.split('\t')
                if (fields.size != 4) return@forEach
                val labelText = decodeField(fields[0])
                val pkg = decodeField(fields[1])
                val cls = decodeField(fields[2])
                val filename = fields[3]
                val bitmapFile = File(dir, filename)
                if (!bitmapFile.exists()) return@forEach
                val target = AppTarget(labelText, pkg, cls, null)
                assignments[target.componentKey] = IconAssignment(target, null, filename)
            }
        } catch (_: Exception) { }
    }

    private fun saveAssignments() {
        val dir = File(filesDir, "assigned_icons")
        dir.mkdirs()
        val lines = mutableListOf<String>()
        assignments.values.forEach { assignment ->
            val target = assignment.target
            val fileName = assignment.fileName.ifBlank { stableFileName(target.componentKey) + ".png" }
            try {
                val bitmap = assignment.bitmap
                if (bitmap != null) {
                    FileOutputStream(File(dir, fileName)).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } else if (!File(dir, fileName).exists()) {
                    return@forEach
                }
                lines.add(listOf(encodeField(target.label), encodeField(target.packageName), encodeField(target.activityName), fileName).joinToString("\t"))
            } catch (_: Exception) { }
        }
        try { File(filesDir, "assignments.tsv").writeText(lines.joinToString("\n")) } catch (_: Exception) { }
    }

    private fun encodeField(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun decodeField(value: String): String = String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)

    private fun stableFileName(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(24)

    @Deprecated("Deprecated in Android API; required for native document picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_IMAGE -> {
                val bitmap = try { decodeImage(uri) } catch (_: Exception) { null }
                if (bitmap == null) {
                    toast("Gambar tidak dapat dibuka. Coba pilih file gambar lain.")
                    return
                }
                customBitmap = bitmap
                saveCustomBitmap(bitmap)
                persistEditorState()
                if (screen != Screen.EDITOR) showScreen(Screen.EDITOR) else refreshPreview()
                toast("Gambar custom berhasil ditambahkan.")
            }
            REQUEST_PNG -> {
                val bitmap = pendingBitmap ?: renderIcon()
                Thread {
                    val success = try {
                        contentResolver.openOutputStream(uri)?.use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        } ?: false
                    } catch (_: Exception) { false }
                    runOnUiThread { toast(if (success) "PNG berhasil disimpan." else "Gagal menyimpan PNG.") }
                }.start()
                pendingBitmap = null
            }
            REQUEST_PACK_ZIP -> {
                val snapshot = assignments.values.map { it.copy() }
                if (snapshot.isEmpty()) {
                    toast("Petakan minimal satu aplikasi terlebih dahulu.")
                    return
                }
                Thread {
                    val success = try {
                        val stream = contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Tidak dapat membuka file tujuan")
                        stream.use { writeIconPackProjectZip(it, snapshot) }
                        true
                    } catch (_: Exception) { false }
                    runOnUiThread { toast(if (success) "ZIP proyek icon pack berhasil disimpan." else "Gagal mengekspor proyek icon pack.") }
                }.start()
            }
        }
    }

    private fun decodeImage(uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while (maxOf(options.outWidth / sample, options.outHeight / sample) > 2048) sample *= 2
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }

    private fun writeIconPackProjectZip(output: OutputStream, iconAssignments: List<IconAssignment>) {
        ZipOutputStream(output.buffered()).use { zip ->
            val rootDir = "IconForge-Launcher-Pack/"
            val drawableNames = linkedMapOf<String, IconAssignment>()
            val used = hashSetOf<String>()
            iconAssignments.forEach { assignment ->
                val base = "icon_" + sanitizeResourceName(assignment.target.packageName + "_" + assignment.target.activityName)
                var name = base
                var suffix = 2
                while (!used.add(name)) { name = "${base}_$suffix"; suffix++ }
                drawableNames[name] = assignment
            }

            val appFilter = buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
                append("    <!-- Generated by IconForge. Components were resolved from installed launcher activities. -->\n")
                drawableNames.forEach { (drawable, assignment) ->
                    val component = "ComponentInfo{${assignment.target.packageName}/${assignment.target.activityName}}"
                    append("    <item component=\"${xmlEscape(component)}\" drawable=\"$drawable\" />\n")
                }
                append("</resources>\n")
            }
            val drawableXml = buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
                drawableNames.keys.forEach { append("    <item drawable=\"$it\" />\n") }
                append("</resources>\n")
            }
            val packGradle = """
                plugins {
                    id("com.android.application") version "8.9.2" apply false
                }
            """.trimIndent() + "\n"
            val settingsGradle = """
                pluginManagement {
                    repositories { google(); mavenCentral(); gradlePluginPortal() }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories { google(); mavenCentral() }
                }
                rootProject.name = "IconForge-Launcher-Pack"
                include(":app")
            """.trimIndent() + "\n"
            val appGradle = """
                plugins { id("com.android.application") }
                android {
                    namespace = "id.riabz.generatediconpack"
                    compileSdk = 35
                    defaultConfig {
                        applicationId = "id.riabz.generatediconpack"
                        minSdk = 23
                        targetSdk = 35
                        versionCode = 1
                        versionName = "1.0.0"
                    }
                }
            """.trimIndent() + "\n"
            val generatedManifest = """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:allowBackup="false" android:label="IconForge Launcher Pack"
                        android:icon="@drawable/icon_pack" android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <meta-data android:name="com.google.android.theme.iconpack.metadata" android:resource="@xml/appfilter" />
                        <activity android:name=".PackActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                            <intent-filter><action android:name="com.novalauncher.THEME" /></intent-filter>
                            <intent-filter><action android:name="org.adw.ActivityStarter.THEMES" /></intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent() + "\n"
            val packActivity = """
                package id.riabz.generatediconpack

                import android.app.Activity
                import android.graphics.Color
                import android.os.Bundle
                import android.view.Gravity
                import android.widget.LinearLayout
                import android.widget.TextView
                import org.xmlpull.v1.XmlPullParser

                class PackActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        val count = try {
                            val parser = resources.getXml(R.xml.appfilter)
                            var total = 0
                            var event = parser.eventType
                            while (event != XmlPullParser.END_DOCUMENT) {
                                if (event == XmlPullParser.START_TAG && parser.name == "item") total++
                                event = parser.next()
                            }
                            parser.close()
                            total
                        } catch (_: Exception) { 0 }
                        val layout = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(32, 32, 32, 32)
                            setBackgroundColor(Color.rgb(245, 247, 251))
                        }
                        layout.addView(TextView(this).apply {
                            text = "IconForge Launcher Pack"
                            textSize = 24f
                            setTextColor(Color.rgb(24, 34, 55))
                            gravity = Gravity.CENTER
                        })
                        layout.addView(TextView(this).apply {
                            text = "${'$'}count ikon dipetakan\n\nBuka pengaturan ikon pada launcher yang kompatibel, lalu pilih IconForge Launcher Pack. Dukungan berbeda pada tiap launcher."
                            textSize = 16f
                            setTextColor(Color.rgb(85, 99, 122))
                            gravity = Gravity.CENTER
                            setPadding(0, 18, 0, 0)
                        })
                        setContentView(layout)
                    }
                }
            """.trimIndent() + "\n"
            val readme = """
                # IconForge Launcher Pack

                Proyek icon pack Android yang dibuat otomatis oleh IconForge.

                ## Build menjadi APK
                1. Ekstrak ZIP ini.
                2. Buka folder `IconForge-Launcher-Pack` dari Android Studio.
                3. Di Linux/Termux, jika perlu, jalankan `chmod +x gradlew` agar wrapper dapat dieksekusi.
                4. Izinkan Gradle mengunduh Android Gradle Plugin/komponen SDK yang diperlukan.
                5. Jalankan **Build > Build APK(s)**.
                6. Instal APK hasil build, lalu pilih paket ini dari pengaturan icon pack launcher yang kompatibel.

                `app/src/main/res/xml/appfilter.xml` memetakan `ComponentInfo{package/activity}` ke drawable PNG.
                PNG disimpan sebagai 512 × 512 px di `drawable-nodpi`, dengan alpha/transparansi dipertahankan.

                ## Kompatibilitas
                Paket memakai format `appfilter.xml` yang umum dipakai oleh launcher yang mendukung icon pack, termasuk Nova Launcher dan launcher berbasis standar ADW. Tidak semua launcher pihak ketiga mendukung format atau alur penerapan yang sama.

                ## Perhatian
                Paket ini tidak mengubah file ikon asli aplikasi dan tidak mengubah launcher bawaan sistem. Ikon pengganti hanya tampil pada launcher yang mendukung icon pack ini.
            """.trimIndent() + "\n"

            addText(zip, rootDir + "settings.gradle.kts", settingsGradle)
            addText(zip, rootDir + "build.gradle.kts", packGradle)
            val wrapperScript = resources.openRawResource(R.raw.gradlew_template).bufferedReader().use { it.readText() }
            val windowsWrapperScript = resources.openRawResource(R.raw.gradlew_template_bat).bufferedReader().use { it.readText() }
            addText(zip, rootDir + "gradlew", wrapperScript)
            addText(zip, rootDir + "gradlew.bat", windowsWrapperScript)
            addText(zip, rootDir + "gradle.properties", "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\n")
            addText(zip, rootDir + "gradle/wrapper/gradle-wrapper.properties", "distributionBase=GRADLE_USER_HOME\ndistributionPath=wrapper/dists\ndistributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip\nnetworkTimeout=10000\nvalidateDistributionUrl=true\nzipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\n")
            addText(zip, rootDir + "app/build.gradle.kts", appGradle)
            addText(zip, rootDir + "app/src/main/AndroidManifest.xml", generatedManifest)
            addText(zip, rootDir + "app/src/main/java/id/riabz/generatediconpack/PackActivity.kt", packActivity)
            addText(zip, rootDir + "app/src/main/res/xml/appfilter.xml", appFilter)
            addText(zip, rootDir + "app/src/main/res/xml/drawable.xml", drawableXml)
            addText(zip, rootDir + "README.md", readme)
            val launcherBitmap = resolveAssignedBitmap(iconAssignments.first())
            addBitmap(zip, rootDir + "app/src/main/res/drawable-nodpi/icon_pack.png", launcherBitmap)
            drawableNames.forEach { (name, assignment) ->
                addBitmap(zip, rootDir + "app/src/main/res/drawable-nodpi/$name.png", resolveAssignedBitmap(assignment))
            }
            addText(zip, rootDir + "app/src/main/res/values/strings.xml", "<resources><string name=\"app_name\">IconForge Launcher Pack</string></resources>\n")
        }
    }

    private fun resolveAssignedBitmap(assignment: IconAssignment): Bitmap {
        assignment.bitmap?.let { return it }
        val storedFile = File(File(filesDir, "assigned_icons"), assignment.fileName)
        return BitmapFactory.decodeFile(storedFile.absolutePath)
            ?: throw java.io.IOException("Ikon tersimpan tidak dapat dibaca: ${assignment.target.packageName}")
    }

    private fun addText(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun addBitmap(zip: ZipOutputStream, path: String, bitmap: Bitmap) {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        zip.putNextEntry(ZipEntry(path))
        zip.write(buffer.toByteArray())
        zip.closeEntry()
    }

    private fun sanitizeResourceName(raw: String): String {
        var clean = raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
        if (clean.isEmpty() || clean.first() !in 'a'..'z') clean = "x_$clean"
        return clean.take(100).trimEnd('_')
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = rounded(SURFACE, 18, 0xFF25334B.toInt(), 1)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = true
    }

    private fun navButton(value: String, action: () -> Unit): TextView = TextView(this).apply {
        text = value
        gravity = Gravity.CENTER
        textSize = 13f
        setOnClickListener { action() }
        background = rounded(SURFACE, 13, 0, 0)
    }

    private fun styleNav(view: TextView, active: Boolean) {
        view.setTextColor(if (active) Color.WHITE else MUTED)
        view.background = rounded(if (active) 0xFF6C63FF.toInt() else SURFACE, 13, if (active) 0 else 0xFF25334B.toInt(), if (active) 0 else 1)
    }

    private fun smallButton(value: String, selected: Boolean = false, action: () -> Unit): TextView = TextView(this).apply {
        text = value
        gravity = Gravity.CENTER
        textSize = 12f
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(if (selected) Color.WHITE else MUTED)
        background = rounded(if (selected) 0xFF6C63FF.toInt() else 0xFF1A263B.toInt(), 12, if (selected) 0 else 0xFF33415B.toInt(), if (selected) 0 else 1)
        setOnClickListener { action() }
    }

    private fun actionButton(value: String, primary: Boolean = true, enabled: Boolean = true, action: () -> Unit): TextView = TextView(this).apply {
        text = value
        gravity = Gravity.CENTER
        textSize = 13f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(if (enabled) Color.WHITE else 0xFF77849B.toInt())
        background = rounded(
            if (primary && enabled) 0xFF6C63FF.toInt() else 0xFF202C41.toInt(),
            13,
            if (primary) 0 else 0xFF34435D.toInt(),
            if (primary) 0 else 1
        )
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.65f
        setOnClickListener { if (enabled) action() }
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int, strokeDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private inner class CheckerboardView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cell = dp(13).toFloat()
            var y = 0f
            var row = 0
            while (y < height) {
                var x = 0f
                var col = 0
                while (x < width) {
                    paint.color = if ((row + col) % 2 == 0) 0xFFE8ECF3.toInt() else 0xFFBFC9D8.toInt()
                    canvas.drawRect(x, y, x + cell, y + cell, paint)
                    x += cell
                    col++
                }
                y += cell
                row++
            }
        }
    }

    private class SimpleTextWatcher(val changed: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }
}
