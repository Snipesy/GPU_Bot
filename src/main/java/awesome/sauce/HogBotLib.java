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

package awesome.sauce;


import java.io.PrintStream;


/**
 * Created by hooge on 7/20/2017.
 */
public class HogBotLib {
    public static class Coordinate
    {
        public Coordinate(int x, int y)
        {
            this.x = x;
            this.y = y;
        }
        public int x;
        public int y;
    }

    static class coordinatePair
    {
        boolean valid = true;
        Coordinate left;
        Coordinate right;
    }

    static class SwapInfo
    {
        static final int MAX_POW_INFOS = 6;
        Coordinate coord;
        SwapInfo(int x, int y)
        {
            coord = new Coordinate(x,y);
        }

        int pows = 0;
        int[] powInfo = new int[MAX_POW_INFOS];


        int rank = 0;
    }

    static float swapScore(int pows, int[] powInfo)
    {
        int score = 0;
        for (int i = 0; i < pows; i++)
        {
            score+=powInfo[i];
        }
        return score * pows;
    }

    static int rankSwap(int pows, int[] powInfo)
    {
        if (pows == -1)
            return NO_MOVE;
        if (pows == 0)
            return NO_POP;
        if (pows == 1 && powInfo[0]==3)
            return RANK_3_IN_A_ROW;
        if (pows == 1 && powInfo[0]==4)
            return RANK_4_IN_A_ROW;
        if (pows == 1 && powInfo[0]==5)
            return RANK_5_IN_A_ROW;
        if (pows == 2 && powInfo[0] == 3 && powInfo[1] == 3)
            return RANK_3_X_3;
        if (pows == 2 && ((powInfo[0]==4 && powInfo[1]==3)
                || (powInfo[0]==3 && powInfo[1]==4) ))
            return RANK_3_X_4;
        if (pows == 2 && ((powInfo[0]==5 && powInfo[1]==3)
                || (powInfo[0]==3 && powInfo[1]==5) ))
            return RANK_3_X_5;
        if (pows == 2 && ((powInfo[0]==4 && powInfo[1]==4)))
            return RANK_4_X_4;
        if (pows == 2 && ((powInfo[0]==4 && powInfo[1]==5)
                || (powInfo[0]==5 && powInfo[1]==4) ))
            return RANK_4_X_5;
        if (pows == 2 && powInfo[0]==5 && powInfo[1]==5)
            return RANK_5_X_5;
        if (pows == 3)
            return RANK_BINGO;
        if (pows == 4)
            return RANK_SEA_DONKEY;
        if (pows >= 5)
            return RANK_VEGAS;

        return NO_POP;
    }

    /**
     * RULES
     *
     * ALWAYS DO VEGAS, SEA, DONKEY, BINGO, if it sees one within 4 moves.
     *
     * ALWAYS DO 5x5 to 3x3 if it sees one within 3 moves.
     *
     * Otherwise pick best.
     */



    static final int NO_MOVE = -1;
    static final int NO_POP = 0;

    // --- LIMP --- Prioritize least moves with upward weight to get these if a higher rank is not found
    static final int RANK_3_IN_A_ROW = 1;
    static final int RANK_4_IN_A_ROW = 2;
    static final int RANK_5_IN_A_ROW = 3;

    // --- 1 to 3 Moves --- Prioritize highest rank as long as it is in 1 to 3 moves.
    static final int RANK_3_X_3 = 4;
    static final int RANK_3_X_4 = 5;
    static final int RANK_3_X_5 = 6;
    static final int RANK_4_X_4 = 7;
    static final int RANK_4_X_5 = 7;
    static final int RANK_5_X_5 = 8;

    // 1 to 4 moves - prioritize highest rank that is in 1 to 4 moves
    static final int RANK_BINGO = 9;

    // 1 to 5 moves - prioritize highest rank
    static final int RANK_SEA_DONKEY = 10;
    static final int RANK_VEGAS = 11;

    // --- Prioritized over limp.
    static final int JELLY = 31;

    // Priotized over jelly if above threshold..
    static final int PUFFER = 30;

    static class Pow {
        Pow(int element) {
            this.element = element;
            this.counts = 0;
        }

        Pow (int element, int counts)
        {
            this.element = element;
            this.counts = counts;
        }

        int element;
        int counts;
    }

    static class SmartConstraint {
        int ymin = 0;
        int ymax = 12;
        int xmin = 0;
        int xmax = 5;

        /**
         * The smart constraint gives an area which at most encompasses the region to support a vegas.
         * Which is a 6 x 5 box.
         * Since we dont really know where we started we need to calculate for 4 pieces above,
         * and 4 pieces below.
         *
         * maxes are not inclusive on upper.
         *
         * @return
         */
        SmartConstraint()
        {

        }

        SmartConstraint(SmartConstraint copy)
        {
            this.ymax = copy.ymax;
            this.ymin = copy.ymin;
            this.xmax = copy.xmax;
            this.xmin = copy.xmin;
        }

        public void push(int centerX, int centerY)
        {

            int tmpymin;
            int tmpymax;
            if (centerY > 3)
                tmpymin = centerY - 4;
            else
                tmpymin = 0;

            if (centerY > 7)
                tmpymax = 12;
            else
                tmpymax = centerY + 5;

            if (tmpymax < ymax)
                ymin = tmpymin;
            if (tmpymin > ymin)
                ymin = tmpymin;
        }
    }

    /**
     * Does a simple swap, no checks.
     * @param arrayToSwapWith
     * @param xCoord
     * @param yCoord
     */
    static void swapAt(int[][] arrayToSwapWith, int xCoord, int yCoord)
    {
        int tmp = arrayToSwapWith[xCoord][yCoord];
        arrayToSwapWith[xCoord][yCoord] = arrayToSwapWith[xCoord+1][yCoord];
        arrayToSwapWith[xCoord+1][yCoord] = tmp;
    }


    static PrintStream out;
    static {
        out = System.out;

        out.println("Hello world");

    }

    public static void copyPieces(int[][] in, int[][] out)
    {
        for (int y = 0; y < 12; y++)
        {
            for (int x = 0; x < 6; x++)
            {
                out[x][y] = in[x][y];
            }

        }
    }
}
