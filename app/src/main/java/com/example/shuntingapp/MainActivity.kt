package com.example.shuntingapp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
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
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private var devID = 0 // 设备号
    private var locomNum = 0 // 调机号
    private var lastWarningTime = System.currentTimeMillis()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        //region 初始化数据
        // 从SharedPreferences加载保存的值
        val savedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        devID = savedPrefs.getString("devID", getString(R.string.DevID))!!.toInt()  // 如果没有则返回默认值
        locomNum =
            savedPrefs.getString("locomNum", getString(R.string.LocomNum))!!.toInt()  // 如果没有则返回默认值

        // 初始化报文参数
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
            @Deprecated("Deprecated in Java")
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
                    Toast.makeText(this, " 未授予权限: $deniedList", Toast.LENGTH_SHORT).show()
                }
            }
        //endregion


        //region 设备号与机车号
        binding.setBtn.setOnClickListener {
            val layout = layoutInflater.inflate(R.layout.activity_settings_dialog, null)
            val devIdET = layout.findViewById<EditText>(R.id.devIdET)
            val locomNumET = layout.findViewById<EditText>(R.id.locomNumET)

            // 设置输入框文本为现有值
            devIdET.setText(devID.toString())
            locomNumET.setText(locomNum.toString())

            AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(layout)
                .setPositiveButton("保存") { dialog, _ ->
                    devID = devIdET.text.toString().toInt()
                    locomNum = locomNumET.text.toString().toInt()

                    // 更新SharedPreferences
                    val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                    with(prefs.edit()) {
                        putString("devID", devID.toString())
                        putString("locomNum", locomNum.toString())
                        apply()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.cancel()
                }
                .show()
        }
        //endregion


        //region 每隔0.3秒POST回执报文
        executor.scheduleAtFixedRate({
            val receiptData = ReceiptMsg(true, devID, locomNum, planNo, lat, lng)
            performRequest(RetrofitClient.apiService.postReceipt("receipt", receiptData),
                onSuccess = {
                    LogUtils.d("服务器已收到回执包")
                },
                onError = { errorMessage ->
                    LogUtils.d("POST回执包: $errorMessage")
                })
        }, 0, 300, TimeUnit.MILLISECONDS)
        //endregion


        //region 每隔1秒GET心跳报文
        executor.scheduleAtFixedRate({
            performRequest(RetrofitClient.apiService.getDynamicEndpoint("heart/${devID}"),
                onSuccess = { responseData ->
                    if (responseData.Message == "心跳包") {
                        val dataJson = responseData.Data
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
                        LogUtils.d("收到非心跳包: ${responseData.Message}")
                    }
                },
                onError = { errorMessage ->
                    LogUtils.d("GET心跳包: $errorMessage")
                    runOnUiThread { binding.ServerStatus.text = "未连接" }
                })
        }, 500, 1000, TimeUnit.MILLISECONDS)
        //endregion


        //region 每隔1秒GET卫星报文
        /*executor.scheduleAtFixedRate({

        }, 1000, 1000, TimeUnit.MILLISECONDS)*/
        //endregion


        //region 每隔5秒GET调车单报文
        executor.scheduleAtFixedRate({
            performRequest(RetrofitClient.apiService.getDynamicEndpoint("plan/${devID}"),
                onSuccess = { responseData ->
                    if (responseData.Message == "作业计划包") {
                        val dataJson = responseData.Data
                        // 通过指定类解析指定Json
                        val data =
                            Gson().fromJson(dataJson, ResponseMsg.ShuntingMsg::class.java)

                        // 获取现在勾序，用于打点执行
                        currentCutNum = data.CurrentCutNum

                        // 更改时间格式
                        val localTime1 = LocalDateTime.parse(data.PlanStTime)
                        val startTime =
                            localTime1.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        val localTime2 = LocalDateTime.parse(data.PlanEndTime)
                        val endTime =
                            localTime2.format(DateTimeFormatter.ofPattern("HH:mm"))

                        // 更新调车单标题
                        runOnUiThread {
                            binding.titleTV.text = getString(
                                R.string.shunting_form_title,
                                data.PlanNo,
                                data.LocomNo,
                                data.PlanType,
                                data.TrainNum,
                                data.Maker,
                                startTime,
                                endTime,
                                data.Order,
                                data.LocomNum
                            )
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
                        LogUtils.d("收到非作业计划包: ${responseData.Message}")
                    }
                },
                onError = { errorMessage ->
                    LogUtils.d("GET作业计划包: $errorMessage")
                })
        }, 1500, 5000, TimeUnit.MILLISECONDS)
        //endregion


        //region 打点发送POST位置报文
        binding.sendLocationBtn.setOnClickListener {
            // 自定义弹窗界面
            val dialogView = layoutInflater.inflate(R.layout.activity_input_dialog, null)
            val dialogTV = dialogView.findViewById<TextView>(R.id.cutTV)
            val dialogET = dialogView.findViewById<EditText>(R.id.cutET)
            dialogTV.text = getString(R.string.location_info, lat, lng)

            // 加载弹窗功能
            val dialog = AlertDialog.Builder(this)
                .setTitle("位置信息")
                .setView(dialogView)
                .setPositiveButton("确定") { _, _ ->
                    // 检查输入是否为数字
                    if (dialogET.text.toString().matches("\\d+".toRegex())) {
                        val lineNo = dialogET.text.toString().toInt()
                        val executionData =
                            ExecutionMsg(true, devID, locomNum, lineNo, lat, lng, currentCutNum)

                        // 进行网络请求
                        performRequest(RetrofitClient.apiService.postExecution(
                            "execute",
                            executionData
                        ),
                            onSuccess = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "打点成功",
                                    Toast.LENGTH_SHORT
                                ).show()
                                LogUtils.d("服务器已收到执行包")
                            },
                            onError = { errorMessage ->
                                Toast.makeText(
                                    this@MainActivity,
                                    "打点失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                                LogUtils.d("POST执行包: $errorMessage")
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
        // 注册ActivityResultLauncher
        imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val imageUri: Uri? = result.data?.data
                    imageUri?.let { uri ->
                        try {
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                val bytes = inputStream.readBytes()
                                val imageBody =
                                    bytes.toRequestBody(
                                        "image/jpeg".toMediaTypeOrNull(),
                                        0,
                                        bytes.size
                                    )
                                val imagePart =
                                    MultipartBody.Part.createFormData(
                                        "image",
                                        "upload.jpg",
                                        imageBody
                                    )

                                // 进行网络请求
                                performRequest(RetrofitClient.apiService.postImage(
                                    "image",
                                    true,
                                    devID,
                                    locomNum,
                                    LocalDateTime.now().toString(),
                                    imagePart
                                ),
                                    onSuccess = {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "上传成功",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        LogUtils.d("服务器已收到图片包")
                                    },
                                    onError = { errorMessage ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            "上传失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        LogUtils.d("POST图片包: $errorMessage")
                                    })
                            }
                        } catch (e: IOException) {
                            Toast.makeText(this, "文件读取失败: ${e.message}", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }

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
                        imagePickerLauncher.launch(intent)
                    } else {
                        Toast.makeText(this, " 未授予权限: $deniedList", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
        }
        //endregion
    }


    private fun <T> performRequest(
        call: Call<T>,
        onSuccess: (T) -> Unit,
        onError: (String) -> Unit
    ) {
        call.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (response.isSuccessful) {
                    // 处理成功的响应
                    response.body()?.let {
                        onSuccess(it)
                    }
                } else {
                    // 服务器响应错误
                    onError("服务器响应错误 ${response.code()} ${response.message()}")
                }
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                // 处理失败的请求
                onError("网络请求失败 ${t.message}")
            }
        })
    }


    private fun checkAndSpeak(isNewWarning: Boolean, longWarning: String?) {
        val currentTime = System.currentTimeMillis()
        // 检查是否已经过了至少五秒
        if (isNewWarning && longWarning != null && (currentTime - lastWarningTime) >= 8000) {
            lastWarningTime = currentTime  // 更新上次报警时间

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


    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        GPSUtils.getInstance(this)!!.unregisterLocationUpdates()
        executor.shutdownNow()
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (ie: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}