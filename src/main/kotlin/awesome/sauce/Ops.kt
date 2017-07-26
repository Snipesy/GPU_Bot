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
 * Operations which work on a gpu.
 */
object Ops {
    fun canSwap(piece1: Int, piece2: Int): Boolean {
        // false if both piecse are the same
        if (piece1 == piece2) {
            return false
        }

        // False if one of the pieces is not between 0 and 6.
        if (piece1 > 6)
            return false
        if (piece2 > 6)
            return false

        return true
    }

    /**
     * Recursive power function for ints.
     */
    fun powInt(a: Int, b: Int): Int {
        if (b == 0) return 1
        if (b == 1) return a
        if ((b % 2 == 0))
            return powInt(a * a, b / 2) //even a=(a^2)^b/2
        else
            return a * powInt(a * a, b / 2) //odd  a=a*(a^2)^b/2

    }
}