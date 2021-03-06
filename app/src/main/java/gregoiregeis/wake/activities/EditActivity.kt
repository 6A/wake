package gregoiregeis.wake.activities

import gregoiregeis.wake.R
import gregoiregeis.wake.views.BrowserView
import gregoiregeis.wake.views.EditorView
import gregoiregeis.wake.helpers.Theme
import gregoiregeis.wake.helpers.getYamlBlock
import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import net.yslibrary.android.keyboardvisibilityevent.util.UIUtil
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.support.v4.drawerLayout
import java.io.File

const val RW_REQUEST_CODE = 0x12

class EditActivity : AppCompatActivity() {
    private lateinit var drawer: DrawerLayout

    lateinit var editor: EditorView
    lateinit var browser: BrowserView

    val wakeDir = Environment.getExternalStorageDirectory().resolve("Wake")
    val themesDir = wakeDir.resolve("themes")
    val prefsFile = wakeDir.resolve("preferences.md")
    var themeFile = themesDir.resolve("black.md")

    private val welcomeFile = wakeDir.resolve("welcome.md")

    lateinit var currentDir: File private set
    lateinit var currentFile: File private set

    lateinit var userTheme: Theme

    val canReadWrite
        get() = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
             && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)  == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askReadWritePermission()

        performSetup()

        currentDir = wakeDir
        currentFile = welcomeFile

        userTheme = Theme("fonts/firacode_retina.ttf", "fonts/firacode_regular.ttf",
                resources.getColor(R.color.colorAccent, theme),
                Color.WHITE, Color.BLACK,
                Color.WHITE, Color.DKGRAY,
                Color.WHITE, Color.GRAY)

        drawer = drawerLayout {
            backgroundColor = userTheme.background

            addDrawerListener(object : DrawerLayout.DrawerListener {
                override fun onDrawerStateChanged(newState: Int) {}
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
                override fun onDrawerClosed(drawerView: View) {}

                override fun onDrawerOpened(drawerView: View) {
                    UIUtil.hideKeyboard(this@EditActivity)
                }
            })

            editor = EditorView(this@EditActivity)
            browser = BrowserView(this@EditActivity).apply {
                view.lparams(height = matchParent, gravity = Gravity.START)
            }

            browser.view.backgroundColor = userTheme.background

            addView(editor.view)
            addView(browser.view)
        }

        editor.initialize()

        if (canReadWrite) {
            if (intent.data != null && openIntent(intent.data)) {
                return
            }

            applyPreferences()

            changeDirectory(currentDir, true)
            openFile(currentFile)
        }
    }

    private fun openIntent(data: Uri): Boolean {
        if (data.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(data.path)

            if (!file.exists()) {
                longSnackbar(editor.mainFrame, getString(R.string.cannot_open, file.absolutePath))

                return false
            }

            changeDirectory(file.parentFile)
            openFile(file)

            return true
        }

        return false
    }

    fun changeDirectory(directory: File, force: Boolean = false) {
        val dir = when (directory.path) {
            ".." -> currentDir.parentFile
            else -> directory
        }

        if (!force && currentDir == dir) {
            return
        }

        currentDir = dir
        browser.updateDirectoryListing(dir)
    }

    fun openFile(file: File) {
        currentFile = file

        drawer.closeDrawer(Gravity.START)

        val text = file.readText()

        editor.lastHash = text.hashCode()
        editor.isDirty = false

        editor.editor.setText(file.readText())
        editor.fab.hide()
    }

    fun applyTheme() {
        userTheme = Theme.fromYaml(getYamlBlock(themeFile))
    }

    fun applyPreferences() {
        val prefs = getYamlBlock(prefsFile)

        (prefs["theme"] as? String)?.let {
            themeFile = themesDir.resolve(it + ".md")
            applyTheme()
        }

        (prefs["see all files"] as Boolean?)?.let {
            browser.adapter.highestDir = if (it) {
                Environment.getRootDirectory()
            } else {
                Environment.getExternalStorageDirectory()
            }
        }
    }

    private fun askReadWritePermission() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), RW_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) = when {
        requestCode == RW_REQUEST_CODE && resultCode == Activity.RESULT_OK -> {
            performSetup()
            changeDirectory(wakeDir)
            openFile(welcomeFile)
        }
        else -> {}
    }

    override fun onBackPressed() {
        if (drawer.isDrawerOpen(Gravity.START)) {
            drawer.closeDrawer(Gravity.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun performSetup() {
        if (!wakeDir.exists()) {
            wakeDir.mkdir()
        }

        val preferences = wakeDir.resolve("preferences.md")

        if (!preferences.exists()) {
            preferences.createNewFile()
            preferences.writeText(getString(R.string.defaultPreferences))
        }

        val themes = wakeDir.resolve("themes")

        if (!themes.exists()) {
            themes.mkdirs()
        }

        val blackTheme = themes.resolve("black.md")

        if (!blackTheme.exists()) {
            blackTheme.createNewFile()
            blackTheme.writeText(getString(R.string.blackTheme))
        }

        val lightTheme = themes.resolve("white.md")

        if (!lightTheme.exists()) {
            lightTheme.createNewFile()
            lightTheme.writeText(getString(R.string.lightTheme))
        }

        val about = wakeDir.resolve("about.md")

        if (!about.exists()) {
            about.createNewFile()
            about.writeText(getString(R.string.about))
        }

        if (!welcomeFile.exists()) {
            welcomeFile.createNewFile()
            welcomeFile.writeText(getString(R.string.defaultFile))
        }
    }
}
