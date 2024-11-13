package cn.archko.reader.antiword

import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

/**
 * @author: archko 2022/7/11 :9:49 上午
 */
class TextActivity : AppCompatActivity() {

    private var input: String = ""
    private var output: String = ""

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        input =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath+"/aa.doc"
        output =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath+"/aa.html"

        setContentView(R.layout.activity_test)
        val button = findViewById<Button>(R.id.btn)
        button.setOnClickListener {
            thread {
                convert()
            }
        }
    }

    private fun convert() {
        AntiWord.convertDocToHtml(input, output)
    }
}