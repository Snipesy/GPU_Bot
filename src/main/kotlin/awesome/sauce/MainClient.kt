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

import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.delay

/**
 * Created by hooge on 7/21/2017.
 */
object MainClient {
    suspend fun main(cb: ClientCallbackListener) {

        val robo = RoboHog()

        try {
            cb.statusUpdate("Running. Searching for client.")
            go(cb, robo)
        }
        catch (e: CancellationException)
        {
            System.out.println("Coroutine cancelled")
            cb.statusUpdate("Coroutine cancelled.")

            try {
                robo.destroy()
            }
            catch (e: Exception)
            {
                System.out.println("Exception while cleaning up")
                e.printStackTrace(System.out)
            }
        }
        catch (e: Exception)
        {
            cb.statusUpdate("Unexpected exception")
            e.printStackTrace(System.out)

            try {
                robo.destroy()
            }
            catch (e: Exception)
            {
                System.out.println("Exception while cleaning up")
                e.printStackTrace(System.out)
            }
        }

        cb.onStop()


    }

    private suspend fun go(cb: ClientCallbackListener, robo: RoboHog)
    {
        val MAX_TIME_WITHOUT_SEEINMG_PIECES = 5000

        val MAX_TIME_WITHOUT_SEEINMG_PIECES_LEEWAY = 2000

        val MAX_TIME_WITHOUT_SEEING_BILGE_WINDOW = 30000;


        robo.findBilgeWindow()


        //robo.goAndClick(1300,500)

        // Initial find window
        var timeWithoutWindowStart = System.currentTimeMillis()
        var timeWithoutSeeingPiecesStart = System.currentTimeMillis()

        while (robo.windowStartX == 0 || robo.windowStartY == 0)
        {
            delay(1500)

            robo.findBilgeWindow()
            if (timeWithoutWindowStart + MAX_TIME_WITHOUT_SEEING_BILGE_WINDOW < System.currentTimeMillis())
            {
                cb.statusUpdate("Cancelled since no window found.")
                System.out.println("Took too long to find a bilge window. Quitting now.")
                return

            }

        }

        cb.statusUpdate("Client found.")
        while (true)
        {
            robo.fillGridColors()

            if (robo.parsePieces())
            {
                robo.go()
            }
            else
            {
                timeWithoutSeeingPiecesStart = System.currentTimeMillis()

                while (robo.fillGridColors() && !robo.parsePieces())
                {
                    if (timeWithoutSeeingPiecesStart + MAX_TIME_WITHOUT_SEEINMG_PIECES_LEEWAY < System.currentTimeMillis())
                    {
                        cb.statusUpdate("Client lost.")
                        timeWithoutSeeingPiecesStart = System.currentTimeMillis()

                        delay(500)

                        while (robo.fillGridColors() && !robo.parsePieces())
                        {
                            if (timeWithoutSeeingPiecesStart + MAX_TIME_WITHOUT_SEEINMG_PIECES < System.currentTimeMillis())
                            {
                                System.out.println("Lost bilge window. Resetting.")
                                go(cb, robo)
                                return

                            }
                            delay(500)
                        }


                    }
                    delay(100)
                }


            }

            delay(500)
        }
    }


}
