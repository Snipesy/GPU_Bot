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

/**
 *  Manages the GPU kernel and goes through all the results.
 */
class HogBotGPU {





    val kernel = HogBotGPUKernel()

    private var latestMove: HogBotGPUKernel.HighLevelResult? = null
    private var latestReccomendation: HogBotLib.Coordinate? = null

    val passes = 3

    fun getLatest(): HogBotLib.Coordinate?
    {
            return latestReccomendation;
    }

    fun getReccomendation(board: Board): HogBotGPUKernel.HighLevelResult? {

        divergeToGpu(board);


        return latestMove;


    }

    fun destroy()
    {
        kernel.dispose()
    }





    /**
     * Diverge to GPU. Pieces pool must be filled at this point.
     */
    fun divergeToGpu(board: Board) {



        val sb = StringBuilder()

        for (y in 11 downTo  0) {
            for (x in 0..5) {
                kernel.encodeOriginalPiece(x,y,board.getPiece(x,y).toByte())
                sb.append(board.getPiece(x,y))
                sb.append(" ")
            }
            System.out.println(sb.toString())
            sb.setLength(0)

        }



        var bestScore = 0f;
        var bestResult = HogBotGPUKernel.HighLevelResult()
        var bestResultForThisPass: HogBotGPUKernel.HighLevelResult


        for (i in 0 until passes)
        {
            kernel.reset()
            kernel.execute(kernel.range)

            var bestIndex = 0
            var best = 0f

            for (n in 0 until kernel.boardsToSolve)
            {
                if (kernel.poolOfBestMoveScore[n] > best)
                {
                    bestIndex = n
                    best = kernel.poolOfBestMoveScore[n]
                }
            }

            bestResultForThisPass = kernel.getHighLevelResult(bestIndex)

            System.out.println("Best score found on pass $i is " + best + " at " + bestIndex + " with " + kernel.poolOfBestMoveMoves[bestIndex] + " moves")

            for ( n in 0 until bestResultForThisPass.moves)
            {
                System.out.println("Move $n: " + bestResultForThisPass.moveInfo[n].x + " " + bestResultForThisPass.moveInfo[n].y)
            }

            //if ((bestResultForThisPass.score * bestResultForThisPass.moves) / (bestResult.moves + bestResultForThisPass.moves) > bestScore)
            if ((bestResultForThisPass.score) > bestScore)
            {
                // This is better than what we had on the previous pass (if we had anything at all)

                bestScore = (bestResultForThisPass.score)

                // If we have some moves remove the last one
                if (bestResult.moves > 0)
                {
                    bestResult.moveInfo.removeAt(bestResult.moveInfo.size-1)
                    bestResult.moves--
                }

                // Add and do up to the last move.
                for (n in 0 until bestResultForThisPass.moveInfo.size-1)
                {
                    val thisMove = bestResultForThisPass.moveInfo[n]
                    bestResult.moveInfo.add(thisMove)
                    bestResult.moves++

                    // Perform swaps on kernel board
                    kernel.performSwapOnSource(thisMove.x, thisMove.y)

                }
                // Add last move
                bestResult.moveInfo.add(bestResultForThisPass.moveInfo[bestResultForThisPass.moveInfo.size-1])
                bestResult.moves++

            }
            else
            {
                // There is no point going further cause we will always get the same result.
                break
            }


        }

        // Finally add the last best move





        for ( i in 0 until bestResult.moves)
        {
            System.out.println("Move $i: " + bestResult.moveInfo[i].x + " " + bestResult.moveInfo[i].y)
        }




        latestMove = bestResult







    }





}
