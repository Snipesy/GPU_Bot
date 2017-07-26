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
 * Created by hooge on 7/21/2017.
 */
object HogBotGpuConsts {

    // Size of bilge board.
    val BILGE_BOARD_SIZE_X = 6
    val BILGE_BOARD_SIZE_Y = 12





    val SIZE_OF_BOARD = BILGE_BOARD_SIZE_Y * BILGE_BOARD_SIZE_X;


    /**
     * Number of CPU rounds before shotgunning to GPU.
     */
    val CPU_ROUNDS = 2

    // Max depth of the algorithim.
    val MAX_DEPTH = 5

    val GPU_RANGE = Ops.powInt(BILGE_BOARD_SIZE_X * BILGE_BOARD_SIZE_Y, CPU_ROUNDS)



}