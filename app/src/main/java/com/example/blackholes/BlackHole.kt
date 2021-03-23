package com.example.blackholes

/**
 * ブラックホールを生成するクラス
 * @param x X座標
 * @param y Y座標
 * @param speed 拡大縮小するスピード
 */
class BlackHole(val x: Float,val y: Float,val speed:Int) {
    val MAX = 400f //半径の最大値
    val MIN = 30f //半径の最小値
    var r = 30f //半径
    var sign = 1 //最大値を超えたら-1 最小値を下回ったら1にする。

    fun grow(){
        if(r > MAX){
            sign = -1
        }else if(r < MIN){
            sign = 1
        }
        //speedとsignを乗算した値をrに足すことで、ブラックホールを大きくしたり、小さくしたりできる。
        r += (speed * sign).toFloat()
    }
}