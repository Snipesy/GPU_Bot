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

import awesome.sauce.Board;
import awesome.sauce.HogBotGPU;
import com.aparapi.device.Device;
import com.aparapi.device.OpenCLDevice;
import org.junit.Test;

public class BreakTest {

    class SimpleBoard implements Board
    {
        int[][] board;
        int waterlevel;
        SimpleBoard(int[][] board, int waterlevel)
        {
            this.board = board;
            this.waterlevel = waterlevel;
        }
        @Override
        public int getPiece(int x, int y) {
            int trueY = Math.abs(y - 11);
            return board[trueY][x];
        }

        @Override
        public int getWaterLevel() {
            return waterlevel;
        }
    }

    @Test
    public void testBreak()
    {
        int[][] exampleBoard = {{7,1,2,3,4,5},
                                {0,3,2,5,2,1},
                                {2,1,0,3,2,1},
                                {0,1,2,3,4,5},
                                {0,3,0,5,2,1},
                                {2,1,2,3,2,9},
                                {0,1,2,5,4,5},
                                {0,3,0,3,2,1},
                                {2,1,2,3,2,1},
                                {0,1,2,5,4,5},
                                {0,3,0,3,2,5},
                                {2,1,2,5,8,5},
                                {2,1,2,2,4,3}};

        SimpleBoard board = new SimpleBoard(exampleBoard, 3);

        HogBotGPU bot = new HogBotGPU(2,3, OpenCLDevice.listDevices(Device.TYPE.GPU).get(0));

        bot.getReccomendation(board);



    }
}
