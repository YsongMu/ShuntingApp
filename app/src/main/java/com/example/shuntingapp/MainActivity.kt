package com.example.shuntingapp

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shuntingapp.databinding.ActivityMainBinding
import com.example.shuntingapp.dsform.ShuntingForm
import com.example.shuntingapp.dsform.ShuntingFormAdapter
import com.example.shuntingapp.message.ExecutionMsg
import com.example.shuntingapp.message.ReceiptMsg
import com.example.shuntingapp.message.ResponseMsg
import com.example.shuntingapp.retrofit.RetrofitClient
import com.example.shuntingapp.utils.GPSUtils
import com.example.shuntingapp.utils.LogUtils
import com.google.gson.Gson
import com.permissionx.guolindev.PermissionX
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var executor: ScheduledExecutorService // 定时器
    private lateinit var shuntingFormAdapter: ShuntingFormAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var textToSpeech: TextToSpeech
    private var devID = 0 // 设备号
    private var locomNum = 0 // 调机号

    companion object {
        private const val REQUEST_CODE_IMAGE_PICK = 102
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        //region 初始化数据
        // 初始化报文参数
        devID = getString(R.string.DevID).toInt()
        locomNum = getString(R.string.LocomNum).toInt()
        var currentCutNum = 0
        var planNo = 0
        var lat = 0.0
        var lng = 0.0

        // 初始化线程池
        executor = Executors.newScheduledThreadPool(5)

        // 初始化动态表格
        recyclerView = findViewById(R.id.recycleView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        shuntingFormAdapter = ShuntingFormAdapter(mutableListOf())
        recyclerView.adapter = shuntingFormAdapter

        // 初始化TTS服务
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    LogUtils.d("语音报警语言不支持")
                } else {
                    LogUtils.d("语音报警初始化成功")
                }
            } else {
                LogUtils.d("语音报警初始化失败")
            }
        }
        //endregion


        // region 5G信号强度
        val telephonyManager =
            getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // 对于Android 10及以下版本，使用旧的API
        val phoneStateListener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                super.onSignalStrengthsChanged(signalStrength)
                // 更新信号强度
                runOnUiThread { binding.FGStatus.text = signalStrength.level.toString() }
            }
        }
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

        // 对于Android 10以上版本，使用新的API
        /*val telephonyCallback = @RequiresApi(Build.VERSION_CODES.S)
        object : TelephonyCallback(),
            TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                val FGlevel = signalStrength.level // 获取信号强度
                binding.FGStatus.text = FGlevel.toString()
            }
        }
        telephonyManager.registerTelephonyCallback(
            mainExecutor,
            telephonyCallback
        )*/
        //endregion


        //region 经纬度与卫星数
        PermissionX.init(this).permissions(Manifest.permission.ACCESS_FINE_LOCATION)
            .request { allGranted, _, deniedList ->
                if (allGranted) {
                    GPSUtils.getInstance(this)!!
                        .getLocation(object : GPSUtils.LocationCallBack {
                            override fun setLocation(location: Location?) {
                                if (location != null) {
                                    runOnUiThread { binding.BDStatus.text = "已连接" }
                                    lat = location.latitude
                                    lng = location.longitude
                                } else {
                                    runOnUiThread { binding.BDStatus.text = "无位置信息" }
                                }
                            }

                            override fun setBeidouSatelliteCount(count: Int) {
                                runOnUiThread { binding.BDStarNum.text = count.toString() }
                            }
                        })
                } else {
                    Toast.makeText(this, " You denied $deniedList", Toast.LENGTH_SHORT).show()
                }
            }
        //endregion


        //region 每隔0.3秒POST回执报文
        executor.scheduleAtFixedRate({
            val receiptData = ReceiptMsg(true, devID, locomNum, planNo, lat, lng)
            RetrofitClient.apiService.postReceipt("receipt", receiptData)
                .enqueue(object : retrofit2.Callback<ApiResponse> {
                    override fun onResponse(
                        call: Call<ApiResponse>,
                        response: Response<ApiResponse>
                    ) {
                        if (response.isSuccessful) {
                            // 处理成功的响应
                            LogUtils.d("服务器已收到回执包")
                        } else {
                            // 服务器返回错误状态码
                            LogUtils.d("服务器错误: HTTP ${response.code()} ${response.message()}")
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        // 处理错误情况
                        LogUtils.d("POST回执包失败: ${t.message}")
                    }
                })
        }, 0, 300, TimeUnit.MILLISECONDS)
        //endregion


        //region 每隔1秒GET心跳报文
        executor.scheduleAtFixedRate({
            RetrofitClient.apiService.getDynamicEndpoint("heart/${devID}")
                .enqueue(object : retrofit2.Callback<ApiResponse> {
                    @RequiresApi(Build.VERSION_CODES.S)
                    override fun onResponse(
                        call: Call<ApiResponse>,
                        response: Response<ApiResponse>
                    ) {
                        if (response.isSuccessful) {
                            // 处理成功的响应
                            if (response.body()?.Message == "心跳包") {
                                val dataJson = response.body()!!.Data
                                // 通过指定类解析指定Json
                                val data = Gson().fromJson(
                                    dataJson,
                                    ResponseMsg.HeartbeatMsg::class.java
                                )

                                // 获取调号，用于回执
                                planNo = data.PlanNo

                                // 语音报警
                                checkAndSpeak(data.IsNewWarning, data.LongWarning)

                                // 更新状态信息
                                runOnUiThread {
                                    binding.ServerStatus.text = "已连接"
                                    binding.LocomNum.text = locomNum.toString()
                                    binding.Speed.text = data.Speed.toString()
                                    binding.Distance.text = data.TenFiveThreeCars
                                    binding.Warning.text = data.ShortWarning
                                }
                            } else {
                                LogUtils.d("收到非心跳包: ${response.body()?.Message}")
                            }
                        } else {
                            LogUtils.d("网络请求失败: ${response.code()} ${response.message()}")
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        // 处理错误情况
                        LogUtils.d("GET心跳包失败")
                        runOnUiThread { binding.ServerStatus.text = "未连接" }
                    }
                })
        }, 500, 1000, TimeUnit.MILLISECONDS)
        //endregion


        //region 每隔1秒GET卫星报文
        /*executor.scheduleAtFixedRate({

        }, 1000, 1000, TimeUnit.MILLISECONDS)*/
        //endregion


        //region 每隔5秒GET调车单报文
        executor.scheduleAtFixedRate({
            RetrofitClient.apiService.getDynamicEndpoint("plan/${devID}")
                .enqueue(object : retrofit2.Callback<ApiResponse> {
                    @RequiresApi(Build.VERSION_CODES.S)
                    override fun onResponse(
                        call: Call<ApiResponse>,
                        response: Response<ApiResponse>
                    ) {
                        if (response.isSuccessful) {
                            // 处理成功的响应
                            if (response.body()?.Message == "作业计划包") {
                                val dataJson = response.body()!!.Data
                                // 通过指定类解析指定Json
                                val data =
                                    Gson().fromJson(dataJson, ResponseMsg.ShuntingMsg::class.java)

                                // 获取现在勾序，用于打点执行
                                currentCutNum = data.CurrentCutNum

                                // 更改时间格式
                                val localTime1 = LocalDateTime.parse(data.PlanStTime)
                                val startTime =
                                    localTime1.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                val localTime2 = LocalDateTime.parse(data.PlanEndTime)
                                val endTime =
                                    localTime2.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

                                // 更新调车单标题
                                runOnUiThread {
                                    binding.titleTV.text =
                                        "第${data.PlanNo}号 ${data.LocomNo}调  ${data.PlanType}${data.TrainNum}次  ${data.Maker}编制\n" +
                                                "计划起止: ${startTime}至${endTime}  ${data.Order}班\n" +
                                                "调机: ${data.LocomNum}"
                                }

                                // 更新调车单表格
                                data.Cuts?.map { cut ->
                                    ShuntingForm(
                                        hook = cut.CutNum,
                                        track = cut.LineName,
                                        pn = cut.PlanType,
                                        count = cut.CarsNum,
                                        content = cut.NoteText
                                    )
                                }?.let { formData ->
                                    runOnUiThread { shuntingFormAdapter.addData(formData) }
                                }
                            } else {
                                LogUtils.d("收到非作业计划包: ${response.body()?.Message}")
                            }
                        } else {
                            LogUtils.d("网络请求失败: ${response.code()} ${response.message()}")
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        // 处理错误情况
                        LogUtils.d("GET作业计划包失败")
                    }
                })
        }, 1500, 5000, TimeUnit.MILLISECONDS)
        //endregion


        //region 打点发送POST位置报文
        binding.sendLocationBtn.setOnClickListener {
            // 自定义弹窗界面
            val dialogView = layoutInflater.inflate(R.layout.activity_input_dialog, null)
            val dialogTV = dialogView.findViewById<TextView>(R.id.cutTV)
            val dialogET = dialogView.findViewById<EditText>(R.id.cutET)
            dialogTV.text = "纬度: ${lat}\n" + "经度: $lng"

            // 加载弹窗功能
            val dialog = AlertDialog.Builder(this)
                .setTitle("位置信息")
                .setView(dialogView)
                .setPositiveButton("确定") { _, _ ->
                    // 检查输入是否为数字
                    if (dialogET.text.toString().matches("\\d+".toRegex())) {
                        // 点击确定按钮后POST位置信息
                        val lineNo = dialogET.text.toString().toInt()
                        val executionData =
                            ExecutionMsg(true, devID, locomNum, lineNo, lat, lng, currentCutNum)
                        RetrofitClient.apiService.postExecution("execute", executionData)
                            .enqueue(object : retrofit2.Callback<ApiResponse> {
                                override fun onResponse(
                                    call: Call<ApiResponse>,
                                    response: Response<ApiResponse>
                                ) {
                                    if (response.isSuccessful) {
                                        // 处理成功的响应
                                        Toast.makeText(
                                            this@MainActivity,
                                            "打点成功",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        LogUtils.d("服务器已收到执行包")
                                    } else {
                                        // 服务器返回错误状态码
                                        LogUtils.d("服务器错误: HTTP ${response.code()} ${response.message()}")
                                    }
                                }

                                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                                    // 处理错误情况
                                    LogUtils.d("POST执行报文失败: ${t.message}")
                                }
                            })
                    } else {
                        // 输入非数字提醒
                        Toast.makeText(this, "请输入数字", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消") { dialog, _ ->
                    // 点击取消按钮后关闭对话框
                    dialog.cancel()
                }
                .create()

            dialog.show()
        }
        //endregion


        //region 拍照上传POST图片报文
        binding.takePhotoBtn.setOnClickListener {
            // 检查权限，获取相册图片
            PermissionX.init(this).permissions(Manifest.permission.READ_EXTERNAL_STORAGE)
                .request { allGranted, _, deniedList ->
                    if (allGranted) {
                        val intent =
                            Intent(
                                Intent.ACTION_PICK,
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            )
                        startActivityForResult(intent, REQUEST_CODE_IMAGE_PICK)
                    } else {
                        Toast.makeText(this, " You denied $deniedList", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
        }
        //endregion
    }


    private fun checkAndSpeak(isNewWarning: Boolean, longWarning: String) {
        if (isNewWarning) {
            textToSpeech.speak(longWarning, TextToSpeech.QUEUE_FLUSH, null, null)
            val dialog = AlertDialog.Builder(this)
                .setTitle("报警信息")
                .setMessage(longWarning)
                .setPositiveButton("收到") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            dialog.show()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMAGE_PICK) {
            val imageUri: Uri? = data?.data
            imageUri?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val imageBody =
                            RequestBody.create("image/jpeg".toMediaTypeOrNull(), bytes)
                        val imagePart =
                            MultipartBody.Part.createFormData("image", "upload.jpg", imageBody)

                        // 进行网络请求
                        RetrofitClient.apiService.postImage(
                            "image",
                            true,
                            devID,
                            locomNum,
                            LocalDateTime.now().toString(),
                            imagePart
                        )
                            .enqueue(object : retrofit2.Callback<ApiResponse> {
                                override fun onResponse(
                                    call: Call<ApiResponse>,
                                    response: Response<ApiResponse>
                                ) {
                                    if (response.isSuccessful) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "上传成功",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "上传失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "上传错误: ${t.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            })
                    }
                } catch (e: IOException) {
                    Toast.makeText(this, "文件读取失败: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        GPSUtils.getInstance(this)!!.unregisterLocationUpdates()
        executor?.shutdownNow()
        try {
            if (!executor?.awaitTermination(1, TimeUnit.SECONDS)!!) {
                executor?.shutdownNow()
            }
        } catch (ie: InterruptedException) {
            executor?.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}