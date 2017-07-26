/*
 * Copyright (c) 2017.
 *
 * This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package awesome.sauce

import kotlinx.coroutines.experimental.delay
import org.jnativehook.mouse.NativeMouseEvent
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.AWTException
import java.awt.event.InputEvent
import java.util.*
import kotlin.collections.ArrayList
import org.jnativehook.mouse.NativeMouseInputListener;
import org.jnativehook.NativeHookException
import org.jnativehook.GlobalScreen




/**
 * The bulk of the actual 'bot', as in mouse movents, clicking, is done here.
 */
class RoboHog : Board, NativeMouseInputListener

{

    val bot = HogBotGPU()
    val windowReds = intArrayOf(69, 69,209, 86, 23)
    val windowGreens = intArrayOf(39, 39,198, 39, 104)
    val windowBlues = intArrayOf(12, 12,133, 11, 135)

    val windowRgbs = intArrayOf(4531980, 4531980,13747845, 5646091,1534087)

    // pieces as they appear, note index 7 = puffer, 8 = crab, 9 = jelly. This is how its done in the game sooo why break tradition.

    // The crab is 0 for non bilged pieces, cause I haven't been able to observe it.
    val pieces = intArrayOf(-11026955 ,-12875882, -15087373,-7806267,-15103798,-16611917,-16286997,-331196, 0, -16711704)
    val piecesBilged = intArrayOf(-14452285,-15178851,-16089662,-13203536,-16096334,-16458548,-16556353,-10187140,-15054981,  -16739394 )

    val listOfSpecialPieces = ArrayList<HogBotLib.Coordinate>()

    val robo = Robot()


    var windowStartX=0;

    var windowStartY=0;

    suspend fun go()
    {
        val move = bot.getReccomendation(this)
        if (move != null)
            executeMove(move)
        else
            System.out.println("Null move, holding.")
    }



    fun parsePieces(): Boolean
    {
        listOfSpecialPieces.clear()
        for (y in 0 until MAX_HEIGHT) {

            for (x in 0 until MAX_WIDTH) {
                val piece = determinePiece(x,(MAX_HEIGHT-1)-y)
                if (piece == -1)
                    return false
                else if (piece == 7 || piece == 9)
                {
                    // parse special
                    if (x < MAX_WIDTH - 1 && determinePiece(x+1,(MAX_HEIGHT-1)-y) != 8)
                        listOfSpecialPieces.add(HogBotLib.Coordinate(x,y))
                    else if (x > 0 && determinePiece(x-1,(MAX_HEIGHT-1)-y) != 8)
                    {
                        listOfSpecialPieces.add(HogBotLib.Coordinate(x-1,y))
                    }
                    // else skip it
                }

                listOfGridBubbles[y][x] = piece
            }
        }
        return true
    }

    override fun getPiece(x: Int, y: Int): Int {
        return listOfGridBubbles[y][x]
    }




    fun determinePiece(x: Int, y: Int): Int {
        for (i in 0 until pieces.size){
            if (pieces[i].equals(listOfGridColors[y][x]))
            {
                return i
            }
        }

        for (i in 0 until piecesBilged.size){
            if (piecesBilged[i].equals(listOfGridColors[y][x]))
            {
                return i;
            }
        }

        //System.out.println("Found invalid at $x x $y. Value is " + listOfGridColors[y][x])

        return -1;
    }

    fun findBilgeWindow(): Boolean
    {
        val area = Rectangle(Toolkit.getDefaultToolkit().screenSize);
        val img = robo.createScreenCapture(area)

        for (y in 0 until area.height)
        {
            for (x in 0 until (area.width-windowReds.size))
            {
                if (compareFrom(x,y,img))
                {
                    System.out.println("Found match at $x x $y")

                    windowStartX = x-97

                    windowStartY = y+27


                    return true


                }
            }

        }
        System.out.println("Did not find window")
        return false
    }

    val MAX_HEIGHT = 12

    val MAX_WIDTH = 6

    val listOfGridColors = ArrayList<ArrayList<Int>>()

    val listOfGridBubbles = ArrayList<ArrayList<Int>>()

    init {
        for (y in 0 until MAX_HEIGHT) {
            listOfGridColors.add(ArrayList())
            listOfGridBubbles.add(ArrayList())
            for (x in 0 until MAX_WIDTH) {
                listOfGridColors[y].add(0)
                listOfGridBubbles[y].add(0)
            }
        }


    }

    fun fillGridColors(): Boolean
    {
        val area = Rectangle(windowStartX, windowStartY, (MAX_WIDTH-1)*45 + 44, (MAX_HEIGHT-1)*45 + 44)
        val img = robo.createScreenCapture(area)




        for (y in 0 until MAX_HEIGHT)
        {
            for (x in 0 until MAX_WIDTH)
            {
                listOfGridColors[y][x] = img.getRGB((45*x) + 22, (45*y) + 22)

            }
        }

        return true
    }

    private suspend fun executeMove(move: HogBotGPUKernel.HighLevelResult)
    {
        if (move.score < 7f && listOfSpecialPieces.isNotEmpty())
        {

            executeSwap(listOfSpecialPieces[0].x,listOfSpecialPieces[0].y)
        }
        else
        {
            move.moveInfo.forEach {
                if (!executeSwap(it.x, it.y))
                    return
            }
        }


        if (rand.nextBoolean())
        {
            slideToFast(currentMouseX + rand.nextInt(300-(250))-250, currentMouseY + rand.nextInt(200-(-200))-200)
        }
        else
        {
            slideToFast(currentMouseX - rand.nextInt(300-(250))-250, currentMouseY + rand.nextInt(200-(-200))-200)
        }


    }

    private suspend fun executeSwap(x: Int,y: Int): Boolean
    {
        System.out.println("Executing $x $y");



        // Get pos of the icon.



        if (!goAndClick(x, y))
        {
            return false
        }

        return true



    }

    private fun compareFrom(x: Int, y: Int, img: BufferedImage): Boolean
    {
        for (i in 0 until windowRgbs.size)
        {
            val color = Color(img.getRGB(x+i,y))
            if (color.red != windowReds[i] || color.blue != windowBlues[i])
                return false
        }

        return true
    }

    var currentMouseX = 0
    var currentMouseY = 0

    var nativeMouseX = 0
    var nativeMouseY = 0

    val rand = Random()


    fun getXMouseClickCoord(x: Int): Int
    {
        return windowStartX + (45*x) + 30 + rand.nextInt(3-(-3))-2
    }

    fun getYMouseClickCoord(y: Int): Int
    {
        return windowStartY + (MAX_HEIGHT-1)*45-(45*y) + 17 + rand.nextInt(4-(-4))-4
    }

    suspend fun goAndClick(x: Int, y: Int):  Boolean
    {
        if (!slideTo(getXMouseClickCoord(x), getYMouseClickCoord(y)))
        {
            waitForMouseSettle()
            if (findBilgeWindow())
            {
                if (!slideTo(getXMouseClickCoord(x), getYMouseClickCoord(y)))
                    return false
            }

            else
                return false
        }


        robo.mousePress(InputEvent.BUTTON1_MASK)
        robo.mouseRelease(InputEvent.BUTTON1_MASK)


        delay(rand.nextInt(250-(200)+1)+200.toLong())

        return true

    }

    suspend fun slideTo(xPos: Int, yPos: Int): Boolean
    {
        return mouseGlide(currentMouseX, currentMouseY, xPos, yPos, (rand.nextInt(700-500)+1)+500, 40)

    }

    suspend fun slideToFast(xPos: Int, yPos: Int): Boolean
    {
        return mouseGlide(currentMouseX, currentMouseY, xPos, yPos, (rand.nextInt(250-150)+1)+150, 20)
    }

    /**
     * Fancy method of gliding the mouse in a arc (based off perfect circle)
     *
     * @TODO there is currently a bug where the mouse loops the wrong way, it need's a check to reverse the start and stop angles.
     *
     * @TODO the circle isn't really human like and can be improved by making the source an oval, or by using a different method.
     *      Then again it's a hell of a lot better than just going straight to it.
     */
    suspend fun mouseGlide(x1: Int, y1: Int, x2: Int, y2: Int, t: Int, n: Int): Boolean {
        try {
            val dt = t / n.toDouble()


            //System.out.println("Moving from $x1, $y1 to $x2, $y2")

            val distance = Math.sqrt(Math.pow((x1-x2).toDouble(),2.toDouble())+Math.pow((y1-y2).toDouble(),2.toDouble()))

            val randRadiusOffset = (((rand.nextInt(100-70)+1)+70).toDouble())/100

            val radius = distance * 2 * randRadiusOffset





            val bisectorSlopeX = x2-x1

            val bisectorSlopeY = y2-y1

            val te = if (rand.nextBoolean())
            {
                Math.sqrt(Math.pow(radius/distance, 2.toDouble()) - (1/4.toDouble()))
            }
            else
            {
                Math.sqrt(Math.pow(radius/distance, 2.toDouble()) - (1/4.toDouble())) * -1
            }

            val centerX = ((x1 + x2)/2)-te*bisectorSlopeY

            val centerY = ((y1 + y2)/2)+te*bisectorSlopeX

            //System.out.println("center is $centerX, $centerY, distance is $distance")



            val startAngle = Math.atan2(y1-centerY, x1-centerX )
            val endAngle = Math.atan2(y2-centerY, x2-centerX )




            val angleStep = (endAngle-startAngle)/(n-1)




            for (step in 1..n) {
                delay(dt.toInt().toLong())

                // Check if mouse is where it is suppose to be
                if (currentMouseX !in (nativeMouseX-10 .. nativeMouseX+10) || currentMouseY !in (nativeMouseY-10 .. nativeMouseY+10))
                {
                    System.out.println("Mouse desync detected, $currentMouseX $currentMouseY and $nativeMouseX $nativeMouseY")
                    return false
                }

                val angle = startAngle+(angleStep*step)

                currentMouseX = (centerX + radius * Math.cos((angle))).toInt()
                currentMouseY = (centerY + radius * Math.sin((angle))).toInt()


                //System.out.println("Moving to $currentMouseX, $currentMouseY, radius is $radius, angle is $angle")

                robo.mouseMove(currentMouseX, currentMouseY)

            }

            currentMouseX = x2

            currentMouseY = y2
            // Just to make sure
            robo.mouseMove(currentMouseX, currentMouseY)
        } catch (e: AWTException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        return true

    }

    val SETTLE_TIME = 5000L
    val SETTLE_TIME_SHORT = 1000L

    suspend fun waitForMouseSettle()
    {


        delay(SETTLE_TIME_SHORT/2)
        currentMouseX = nativeMouseX
        currentMouseY = nativeMouseY
        delay(SETTLE_TIME_SHORT/2)

        if (currentMouseX !in (nativeMouseX-10 .. nativeMouseX+10) || currentMouseY !in (nativeMouseY-10 .. nativeMouseY+10))
        {
            waitForMouseSettleLong()
        }


    }
    suspend fun waitForMouseSettleLong()
    {
        do {
            System.out.println("Long sleep, $currentMouseX $currentMouseY and $nativeMouseX $nativeMouseY");
            currentMouseX = nativeMouseX
            currentMouseY = nativeMouseY
            delay(SETTLE_TIME)


        } while (currentMouseX !in (nativeMouseX-10 .. nativeMouseX+10) || currentMouseY !in (nativeMouseY-10 .. nativeMouseY+10))
    }


    override fun nativeMouseClicked(p0: NativeMouseEvent?) {
        // Dont care
    }

    override fun nativeMouseDragged(p0: NativeMouseEvent?) {
        // Dont care
    }

    override fun nativeMouseMoved(p0: NativeMouseEvent?) {

        if (p0 == null)
            return
        // This we do care about.
        nativeMouseX = p0.x
        nativeMouseY = p0.y

    }

    override fun nativeMousePressed(p0: NativeMouseEvent?) {
        // Dont care
    }

    override fun nativeMouseReleased(p0: NativeMouseEvent?) {
        // Dont care
    }

    init {
        try {
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeMouseMotionListener(this)
        } catch (ex: NativeHookException) {
            System.err.println("There was a problem registering the native hook.")
            System.err.println(ex.message)
        }

    }

    fun destroy()
    {
        GlobalScreen.removeNativeMouseMotionListener(this)
        bot.destroy()
    }


    companion object {

    }
}
