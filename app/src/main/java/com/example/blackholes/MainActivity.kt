package com.example.blackholes

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import com.example.blackholes.databinding.ActivityMainBinding
import kotlin.math.floor

/**
 * SurfaceHolder.CallBackインターフェースを実装する。
 */
class MainActivity : AppCompatActivity(),SensorEventListener,SurfaceHolder.Callback {
    private val tag = "Black holes"
    //
    private val matrixSize = 16
    private val numOfBlackHole = 5 //出現させるブラックホール数 後から追加
    private val radius = 30f //ボールの半径
    private val limitOfBlackHole = 100 // ブラックホールがサーフェスの端に出ないようにする。　後から追加

    private var mgValues = FloatArray(3) //配列　センサの値
    private var acValues = FloatArray(3) //配列　センサの値
    private var startTime:Long = 0 //開始時間
    private var surfaceWidth = 0 //surfaceViewの幅
    private var surfaceHeight = 0 //surefaceViewの高さ
    private var ballX = 0f //ボールを出現させるx座標
    private var ballY = 0f //ボールを出現させるy座標
    private var isGoal = false
    private var isGone = false //ブラックホールにボールが飲み込まれたかどうか判定する。　後から追加

    private val blackHoleList = ArrayList<BlackHole>() //BlackHoleインスタンスをnumOfBlackHoleの数だけ入れる

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager:SensorManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //surfaceViewは高速な画面描画が必要な時に用いられる。
        //surfaceView.holderのインスタンスを変数holderに格納する
        val holder = binding.surfaceView.holder
        //surfaceViewイベントの通知先を自分自身に指定する。これで、次に呼ばれるメソッドはsurfaceCreated()になる
        holder.addCallback(this)
    }

    /**
     * センサーの値が更新されたときに呼び出される
     * 求めるpitch(傾斜角)とroll(回転角)に従ってボールを動かしていく
     * @param event
     */
    override fun onSensorChanged(event: SensorEvent?) {
        val inR = FloatArray(matrixSize)
        val outR = FloatArray(matrixSize)
        val I = FloatArray(matrixSize)
        val orValues = FloatArray(3)

        if(event == null) return
        when(event.sensor.type){
            Sensor.TYPE_ACCELEROMETER -> acValues = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> mgValues = event.values.clone()
        }

        //加速度センサと地磁気センサの値から、回転行列inR,Iを作成する。
        SensorManager.getRotationMatrix(inR,I,acValues,mgValues)

        //inRを異なる座標軸系へ行列変換してoutRに出力する。
        SensorManager.remapCoordinateSystem(inR,SensorManager.AXIS_X,SensorManager.AXIS_Y,outR)
        //方位角、傾斜角、回転角を配列として取得する。
        SensorManager.getOrientation(outR,orValues)

        val pitch = rad2Deg(orValues[1])
        val roll = rad2Deg(orValues[2])
        Log.v(tag,"pitch${pitch}")
        Log.v(tag,"roll${roll}")
        if(!isGoal && !isGone){// isGoal = falseかつisGone = falseの時だけ呼び出す(second commit)
            drawGameBoard(pitch,roll)
        }
    }

    /**
     * ゲームの盤面やボールを描画する処理
     * @param pitch
     * @param roll
     */
    private fun drawGameBoard(pitch:Int,roll:Int){
        ballX += roll //rollは左右の動きのためballXに加算する
        ballY -= pitch //pitchは前後の動きのため、ballYから減算する
        //左右は逆から
        if(ballX < 0){ //左->右
            ballX = surfaceWidth - radius //右から出るようにする
        }else if(ballX > surfaceWidth){ //右->左
            ballX = radius //左からでるようにする
        }
        //上はゴール、下は落ちない
        if(ballY + radius < 0){
            isGoal = true
        }else if(ballY + radius > surfaceHeight){
            ballY = surfaceHeight - radius //下限を設定する
        }
        //飲み込まれたか(second commit)
        for(bh in blackHoleList){
            if(checkGone(bh.x,bh.y,bh.r)){
                isGone = true
            }
        }

        //実際の描画処理(ダブルバッファリングを使う)描画時のちらつきを抑える。
        val canvas = binding.surfaceView.holder.lockCanvas() //surfaceの表示をロックしてCanvasオブジェクトを返す
        val paint = Paint() //Paintインスタンスの生成
        canvas.drawColor(Color.BLUE) //背景を塗る
        //ここからsecond commit ブラックホールの描画
        paint.color = Color.BLACK
        for(bh in blackHoleList){ //ブラックホールの数だけ描画
            canvas.drawCircle(bh.x,bh.y,bh.r,paint)
            bh.grow()
        }//ここまでsecond commit
        paint.color = Color.YELLOW
        if(!isGone){//second commit isGoneがfalseの時だけボールを描画する
            canvas.drawCircle(ballX,ballY,radius,paint)  //円がを描画する。中心点の座標と半径を指定する
        }
        //
        if(isGoal){
            paint.textSize = 80f //フォントサイズの指定
            canvas.drawText(goaled(),10f,(surfaceHeight - 60).toFloat(),paint) //座標を指定して、goaled()の戻り値を描画する
        }
        binding.surfaceView.holder.unlockCanvasAndPost(canvas) //SurfaceViewを更新する。lockCanvasとセットで実行する
    }

    /**
     * ボールがブラックホールに飲み込まれたか否かを判定する。(second commit)
     * @param x0 ブラックホールのx座標
     * @param y0 ブラックホールのy座標
     * @param r ブラックホールの半径
     */
    private fun checkGone(x0:Float,y0:Float,r:Float):Boolean{
        //ボールの中心座標がx0,y0を中心座標とする半径rに内包されているかが判定。
        return (x0 - ballX) * (x0 - ballX) + (y0 - ballY) * (y0 - ballY) < r * r
    }

    /**
     * ゴールした時間を返す。
     */
    private fun goaled():String{
        //経過時間
        val elapsedTime = System.currentTimeMillis() - startTime
        val secTime = (elapsedTime / 1000).toInt() //経過時間を秒単位にする
        return "Goal! $secTime"
    }

    private fun rad2Deg(rad:Float):Int{
        return floor(Math.toDegrees(rad.toDouble())).toInt()
    }

    /**
     * @param sensor
     * @param accuracy
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    /**
     * SurfaceViewが生成されたとき
     * 加速度センサと地磁気センサを取得してリスナー登録する
     * @param holder
     */
    override fun surfaceCreated(holder: SurfaceHolder) {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorManager.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this,magField,SensorManager.SENSOR_DELAY_NORMAL)
    }

    /**
     * SurfaceViewが変更されたとき
     * @param holder
     * @param format
     * @param width surfaceViewの幅
     * @param height surfaceViewの高さ
     */
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        //surfaceViewは左上が原点で右に進むとsurfaceWidthに近づき、下に進むとsurfaceHeightに近づく
        surfaceWidth = width
        surfaceHeight = height
        //真ん中あたりの座標
        ballX = (width / 2).toFloat()
        //底辺の近くの座標(高さ-ボールの半径)
        ballY = (height - radius).toFloat()
        //開始時間を記録(1970年1月1日からの経過時間をミリ秒表記)
        startTime = System.currentTimeMillis()
        bornBlackHoles() //後から追記(second commit)
    }

    /**
     * BlackHoleインスタンスを生成してblackHoleListに追加する。
     * 後から追記(seccond commit)
     */
    private fun bornBlackHoles(){
        //numOfBlackHoleの数だけブラックホールを生成する。
        for(i in 1..numOfBlackHole){
            //x,y座標は乱数を使って値を指定する。
            //開始値はlimitOfBlackHoleに指定して、x軸の場合はsurfaceWith - limitOfBlackHoleを終了値に設定
            val x:Float = (limitOfBlackHole..surfaceWidth - limitOfBlackHole).random().toFloat()
            //終了値はsurfaceHeight - limitOfBlackHoleに設定する。->ブラックホールが画面の端に出ないようにする。
            val y:Float = (limitOfBlackHole..surfaceHeight - limitOfBlackHole).random().toFloat()
            //speedも乱数生成する。
            val speed :Int = (2..11).random()
            val bh = BlackHole(x,y,speed)
            blackHoleList.add(bh)
        }
    }

    /**
     * SurfaceViewが破棄されたとき
     * @param holder
     */
    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }
}