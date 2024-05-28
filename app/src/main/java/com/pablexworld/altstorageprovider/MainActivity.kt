package com.pablexworld.altstorageprovider

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.pablexworld.altstorageprovider.ui.theme.AltStorageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )
        actionBar?.hide()
        super.onCreate(savedInstanceState)
        setContent {
            AltStorageTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center

                )
                {
                    FilledTonalButton(onClick = { onClick() }) {
                        Text("Cargar sistema de archivos")
                    }
                }
            }
        }
    }

    private fun onClick() {
        val authority = "com.pablexworld.altstorage.documents"
        val documentId = "/storage/emulated/0"
        val uri = DocumentsContract.buildTreeDocumentUri(authority, documentId)
        val intent = Intent()
        intent.setData(uri)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}

@Preview
@Composable
fun MainActivityRoot() {
    AltStorageTheme {
        FilledTonalButton(onClick = { }) {
            Text("Cargar sistema de archivos")
        }
    }
}