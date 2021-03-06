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


import com.aparapi.Kernel;
import com.aparapi.Range;
import com.aparapi.device.Device;
import com.aparapi.device.OpenCLDevice;
import com.aparapi.internal.kernel.KernelManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * The Mother of this entire program.
 *
 * Works using a sort of divide and conquer strategy.
 *
 * It divides the board into MAX_MOVES_PER_BOARD^N.
 *
 * Where N is the level of divergence.
 *
 * Each of those boards uses a predetermined starting combination.
 * (A level of divergence of 2 will have 2 starting combinations)
 *
 * after those first 2 predetermined moves are done, a different algorithim solves the remaining mooves (MAX_POSSIBLE_DEPTH)
 *
 * @TODO Add ability to abort the execution. I'm not sure how well it would work.
 *
 * @TODO Possible methods of improvements.
 *
 * A big loss of the bot's potential is it does not consider double moves.
 *
 * Instead of simply returning when a swap is detected (scoring it, and going on, etc).
 * It should rank the board, as normal, but continue going forward and see if any new moves can be done on the board post-break.
 *
 * This means some code needs to be written which will take a board, and do all the necessary swaps to make it turn into the board post-break.
 * And it needs to be done without drastically decreasing performance.
 *+
 *
 */
public class HogBotGPUKernel extends Kernel {

    // These parameters are pretty important.
    // Decreasing one or the other will significantly decrease memory use (at cost of losing a level of depth)
    // Note a level of divergence of 3 is really buggy. Not sure why.
    // @TODO fix the 3 level divergence bug. I think it's complicated JIT compiler bugs, so easier said than done.
    int LEVELS_OF_DIVERGENCE = 2;

    int MAX_POSSIBLE_DEPTH = 3;

    int TOTAL_DEPTH = LEVELS_OF_DIVERGENCE + MAX_POSSIBLE_DEPTH;



    final int MAX_POSSIBLE_HEIGHT = 12;

    final int MAX_POSSIBLE_WIDTH = 6;

    final int MAX_SIZE_MOVES = (MAX_POSSIBLE_WIDTH - 1) * MAX_POSSIBLE_HEIGHT;

    public final int boardsToSolve;

    final int STACK_SPACE_FOR_EACH_LEVEL = 2;

    int STACK_SIZE = MAX_POSSIBLE_DEPTH * STACK_SPACE_FOR_EACH_LEVEL;

    final int BOARD_SIZE = MAX_POSSIBLE_HEIGHT * MAX_POSSIBLE_WIDTH;

    private static final int EMPTY_PIECE = 27;





    public HogBotGPUKernel(int levels_of_divergence, int max_possible_depth, OpenCLDevice deviceIn)
    {
        LEVELS_OF_DIVERGENCE = levels_of_divergence;

        MAX_POSSIBLE_DEPTH = max_possible_depth;

        TOTAL_DEPTH = LEVELS_OF_DIVERGENCE + MAX_POSSIBLE_DEPTH;

        STACK_SIZE = MAX_POSSIBLE_DEPTH * STACK_SPACE_FOR_EACH_LEVEL;





        /**
         * The sizes are big enough to accept a worse case scenario.
         */
        int boards = 1;

        for (int i = 0; i < LEVELS_OF_DIVERGENCE; i++)
        {
            boards = boards * MAX_SIZE_MOVES;
        }

        this.boardsToSolve = boards;

        // Biggest array, needs to hold a board for each instance of the GPU.
        this.originalPieces = new byte[BOARD_SIZE];




        if (deviceIn != null)
        {
            System.out.println("Kernel starting with " + deviceIn.getName());
            LinkedHashSet<Device> dSet = new LinkedHashSet<>();

            dSet.add(deviceIn);

            KernelManager.instance().setPreferredDevices(this, dSet);
        }
        else {
            System.out.println("Kernel starting with JTP");

           this.setExecutionModeWithoutFallback(EXECUTION_MODE.JTP);

        }

        System.out.println("Depth is " + max_possible_depth + " and abstraction is " + levels_of_divergence);

        if (LEVELS_OF_DIVERGENCE <= 1)
        {
            this.range = Range.create(MAX_SIZE_MOVES);

        }
        else if (LEVELS_OF_DIVERGENCE == 2)
        {
            this.range = Range.create2D(MAX_SIZE_MOVES, MAX_SIZE_MOVES);
        }
        else
        {
            this.range = Range.create3D(MAX_SIZE_MOVES, MAX_SIZE_MOVES, MAX_SIZE_MOVES);
        }




        int localSize = range.getLocalSize(0) * range.getLocalSize(1) * range.getLocalSize(2);


        // Holds the score of all the best moves.
        this.poolOfBestMoveScore = new float[boardsToSolve];

        this.poolOfBestMoveInfo = new byte[boardsToSolve * TOTAL_DEPTH * 2];

        this.poolOfBestMoveMoves = new byte[boardsToSolve];

        this.poolOfBestMoveMovesToFirst = new byte[boardsToSolve];


        // Arg localStack to emulate recursion in the GPU (it's shallow, so it's fine).
        this.localStack = new byte[localSize * STACK_SIZE];

        this.localstackSize = new byte[localSize];

        this.localyConstraintBuffer = new byte[localSize * MAX_POSSIBLE_DEPTH * 2];

        this.localPiecesBuffer = new byte[localSize * BOARD_SIZE];





    }

    public void reset()
    {

    }

    public static class HighLevelResult
    {
        public int moves = 0;
        public float score = 0f;
        public int movesToFirst = 0;

        public ArrayList<HogBotLib.Coordinate> moveInfo = new ArrayList<>();

        public float getAdjustedScore(int movesLeadingToThis)
        {
            float scoreWithoutMoves = score * moves;

            return scoreWithoutMoves / (moves + movesLeadingToThis);
        }
    }

    public HighLevelResult getHighLevelResult(int N)
    {
        HighLevelResult result = new HighLevelResult();
        result.score = poolOfBestMoveScore[N];
        result.moves = poolOfBestMoveMoves[N];
        result.movesToFirst = poolOfBestMoveMovesToFirst[N];


        for (int i = 0; i < result.moves; i++)
        {
            HogBotLib.Coordinate coord = new HogBotLib.Coordinate(poolOfBestMoveInfo[(N * TOTAL_DEPTH * 2) + i * 2], poolOfBestMoveInfo[(N * TOTAL_DEPTH * 2) + ( i * 2) + 1]);
            result.moveInfo.add(coord);
        }

        return result;


    }



    /**
     * Encodes 2d pieces array to 1d array.
     * TO decode use INDEX%BILGE_BOARD_SIZE_X to get the X index for the 2d array,
     * and INDEX/BILGE_BOARD_SIZE_X to get y index for the 2d array.
     * @param
     */
    public void encodeOriginalPiece(int x, int y, byte value)
    {
        originalPieces[(MAX_POSSIBLE_WIDTH*y)+x] = value;
    }

    /**
     * Decodes element from 1d array.
     * @param x
     * @param y
     * @return
     */
    public byte decodeOriginalPiece(int x, int y)
    {
        return originalPieces[(MAX_POSSIBLE_WIDTH*y)+x];
    }







    public final Range range;



    /**
     * Max possible depth the instance will go to.
     */




    //Piece Arrays to use for GPU threads
    protected final byte[] originalPieces;

    public byte waterLevel = 0;

    public final float[] poolOfBestMoveScore;

    protected final byte[] poolOfBestMoveInfo;

    public final byte[] poolOfBestMoveMoves;

    public final byte[] poolOfBestMoveMovesToFirst;



    // Local stacks.

    @Local final byte[] localStack;

    @Local final byte[] localstackSize;

    @Local final byte[] localyConstraintBuffer;

    @Local final byte[] localPiecesBuffer;





    /**
     * Description of general algorihim.
     *
     * 1. Swap Piece
     * 2. Check for a solve.
     *  a. Solved? Rank it. Test it, etc.
     *  b. Not solved? Go to step 1.
     *
     * 3.
     */


    private int getGlobalArrayIndex()
    {
        if (LEVELS_OF_DIVERGENCE <= 1)
        {
            return getGlobalId(0);
        }
        else if (LEVELS_OF_DIVERGENCE == 2)
        {
            return ((getGlobalId(1)*getGlobalSize(0)) + getGlobalId(0));
        }
        else
        {
            return (getGlobalId(2)*getGlobalSize(1)*getGlobalSize(0) + getGlobalId(1)*getGlobalSize(0) + getGlobalId(0));
        }
    }

    private int getLocalArrayIndex()
    {


        if (LEVELS_OF_DIVERGENCE <= 1)
        {
            return getLocalId(0);
        }
        else if (LEVELS_OF_DIVERGENCE == 2)
        {
            return ((getLocalId(1)*getLocalSize(0)) + getLocalId(0));
        }
        else
        {
            return (getLocalId(2)*getLocalSize(1)*getLocalSize(0) + getLocalId(1)*getLocalSize(0) + getLocalId(0));
        }

    }


    @Override
    public void run() {

        final int P = getLocalArrayIndex();
        final int N = getGlobalArrayIndex();


        // Copy original set to buffer.
        copySourceToLocalBuffer(P);

        initThis(N,P);

        initialiseConstraint(N,P);



        /**
         * Phase 1, Diverge.
         */





        if (!divide(N,P))
        {
            return;
        }

        //System.out.println("Pass");


        /**
         * Phase 2, extrapolate.
         */




        // Put in initial localStack value
        pushStack(P, getFirstConstrainedY(P,0)); // The Initial Y Value
        pushStack(P, getFirstConstrainedX(P,0)); // The Initial X Value




        while(!stackIsEmpty(P))
        {
            recursiveRound(P, N);
        }

    }

    private void initThis(int N, int P)
    {
        localstackSize[P] = 0;
        poolOfBestMoveMoves[N] = 0;
        poolOfBestMoveScore[N] = 0f;
        poolOfBestMoveMovesToFirst[N] = 0;
    }


    private boolean divide(int N, int P)
    {

        int x = getGlobalId(0)%(MAX_POSSIBLE_WIDTH-1);
        int y = getGlobalId(0)/(MAX_POSSIBLE_WIDTH-1);

        poolOfBestMoveInfo[(N * TOTAL_DEPTH * 2)] = (byte)x;
        poolOfBestMoveInfo[(N * TOTAL_DEPTH * 2) + 1] = (byte)y;


        if (LEVELS_OF_DIVERGENCE >= 1)
        {
            if (divideStep(N,P,x,y,(byte)1))
            {
                //System.out.println("P1 Fail with x " + x + " y " + y + " id " + getGlobalId() + " with element X " + elementAt(P,x,y) + " " + elementAt(P,x+1,y));
                return false;
            }

        }
        if (LEVELS_OF_DIVERGENCE >= 2)
        {
            x = getGlobalId(1)%(MAX_POSSIBLE_WIDTH-1);
            y = getGlobalId(1)/(MAX_POSSIBLE_WIDTH-1);

            poolOfBestMoveInfo[(N * TOTAL_DEPTH * 2) + 2] = (byte)x;
            poolOfBestMoveInfo[(N * TOTAL_DEPTH * 2) + 3] = (byte)y;


            if (divideStep(N,P,x,y,(byte)2))
            {
                //System.out.println("P2 Fail");
                return false;
            }
        }
        if (LEVELS_OF_DIVERGENCE >= 3)
        {
            x = getGlobalId(2)%(MAX_POSSIBLE_WIDTH-1);
            y = getGlobalId(2)/(MAX_POSSIBLE_WIDTH-1);

            poolOfBestMoveInfo[(N * TOTAL_DEPTH * 2) + 4] = (byte)x;
            poolOfBestMoveInfo[(N * TOTAL_DEPTH * 2) + 5] = (byte)y;

            if (divideStep(N,P,x,y,(byte)3))
            {
                return false;
            }
        }

        return true;


    }

    private boolean liesInConstraint(int P, int x, int y, int n)
    {
        if (x >= MAX_POSSIBLE_WIDTH || x < 0)
        {
            return false;
        }
        else if (y >= getMaxYConstraint(P, n) || y < 0)
        {
            return false;
        }

        return true;
    }

    private boolean divideStep(int N, int P, int x, int y, byte moves)
    {
        // Should we even attempt?
        if (canSwap(elementAt(P,x,y), elementAt(P,x+1,y)))
        {
            // Do the swap
            performSwap(P,x,y);

            float swapRank = rankBoard(P, x, y);

            pushConstraint(P,0,y);


            if (swapRank >= 0.01f)
            {
                poolOfBestMoveScore[N] = swapRank/(float)moves;
                poolOfBestMoveMoves[N] = moves;

                if (poolOfBestMoveMovesToFirst[N] == 0)
                    poolOfBestMoveMovesToFirst[N] = moves;

                // Reset constraint since its pretty much a new board.
                initialiseConstraint(N,P);

                // Solve board

                breakIntoCheckAll(P,x,y);
            }
        }
        else if (isJellyFish(elementAt(P,x,y)) && isValidPiece(elementAt(P,x+1,y)))
        {
            if (poolOfBestMoveMovesToFirst[N] == 0)
                poolOfBestMoveMovesToFirst[N] = moves;
            // Jelly Logic, jelly is on the left
            initialiseConstraint(N,P);
            jellyIntoCheckAll(P,x,y, false);

        }
        else if (isJellyFish(elementAt(P,x+1,y)) && isValidPiece(elementAt(P,x,y)))
        {
            if (poolOfBestMoveMovesToFirst[N] == 0)
                poolOfBestMoveMovesToFirst[N] = moves;
            // Jelly Logic, jelly is on the right
            initialiseConstraint(N,P);
            jellyIntoCheckAll(P,x,y, true);

        }
        else if (isPufferFish(elementAt(P, x,y)) && isValidPiece(elementAt(P,x+1,y)) )
        {
            if (poolOfBestMoveMovesToFirst[N] == 0)
                poolOfBestMoveMovesToFirst[N] = moves;
            // Puffer Logic, Puffer is on the left
            initialiseConstraint(N,P);
            pufferIntoCheckAll(P,x,y, false);
        }
        else if (isPufferFish(elementAt(P, x+1,y)) && isValidPiece(elementAt(P,x,y)) )
        {
            if (poolOfBestMoveMovesToFirst[N] == 0)
                poolOfBestMoveMovesToFirst[N] = moves;
            // Puffer Logic, Puffer is on the right
            initialiseConstraint(N,P);
            pufferIntoCheckAll(P,x,y, true);
        }
        else
        {
            //System.out.println(x + " " + y + " swap fail");
            // Crab, empty piece, or something like that. Whatever it is were done here this is an invalid combo.
            return true;
        }


        return false;
    }



    /**
     * copies source to local buffer.
     *
     * Itried to make a boolean switch which would say whether or not this is already set
     * I then had the swaps reversed to restore the local buffer to normal.
     *
     * But, absolutely no performacne was gained. Which means the JIT compiler catches this, or I missed something.
     *
     * @param P
     */
    private void copySourceToLocalBuffer(int P)
    {
        for (int i = 0; i < BOARD_SIZE; i++)
        {
            localPiecesBuffer[(P*BOARD_SIZE)+i] = originalPieces[i];
        }
    }

    /**
     * Recursion operations
     */

    private void recursiveRound(int P, int N)
    {
        byte testX = popStack(P);
        byte testY = popStack(P);




       // System.out.println("Popped " + testX + " and " + testY + " at level " + (stackSize(P)/2) + " where Y min is " + getMinYConstraint(P,stackSize(P)/2) + " and max is " + getMaxYConstraint(P, stackSize(P)/2));


        // Check if doing a swap is possible.
        if (!canSwap(elementAt(P,testX,testY),elementAt(P,testX+1,testY)))
        {
            //System.out.println("Cant swap " + testX + " and " + testY);
            // If it is not we just push the next swap.
            if (pushNextIfInRegion(P, testX, testY))
                return;
            else
            {
                revertToPreviousLevelIfExists(P);
                return;
            }
        }

        // Do the swap.
        performSwap(P, testX, testY);

        //System.out.println("Swapping: " + testX + " " + testY + " depth: " + localstackSize(P));


        float swapRank = rankBoard(P, testX, testY);

        //System.out.println("Rank is  " + swapRank);

        // if rank is greater than 0, we got a pow.

        if (swapRank >= 0.01f)
        {
            checkPow(N,P, (stackSize(P)/2)+1+LEVELS_OF_DIVERGENCE, swapRank);

            // Swap back this piece.
            performSwap(P, testX, testY);




            if (pushNextIfInRegion(P, testX, testY))
                return;
            else
            {
                revertToPreviousLevelIfExists(P);
                return;
            }
        }
        else {

            // Check if we can go down the rabbit hole
            if (stackSize(P) >= STACK_SIZE - (STACK_SPACE_FOR_EACH_LEVEL))
            {
                // Revert
                performSwap(P, testX, testY);
                if (pushNextIfInRegion(P, testX, testY))
                {
                    return;
                }
                else
                {
                    //System.out.println("Con1-2: " + stackSize(P));
                    revertToPreviousLevelIfExists(P);
                    return;
                }

            }
            else
            {
                //System.out.println("Con2: " + localstackSize(P));


                // copy this constraint to next
                int level = (stackSize(P) / STACK_SPACE_FOR_EACH_LEVEL);

                copyConstraint(P, level, level+1);

                //System.out.println("Before push Y min is " + getMinYConstraint(P,level+1) + " and max is " + getMaxYConstraint(P, level+1));



                //System.out.println("Pushing X: " + testX +  " Y: " + testY);


                // Push this move into the localStack
                pushConstraint(P,(level+1), testY);



                //System.out.println("After push Y min is " + getMinYConstraint(P,level+1) + " and max is " + getMaxYConstraint(P, level+1));


                //System.out.println("Rabbit to " + (level+1) + "New Y Min " + getMinYConstraint(P, level+1));

                // Go down the rabbit hole.
                // Restore localStack.
                pushStack(P, testY);
                pushStack(P, testX);


                // Put in initial value.
                pushStack(P, getFirstConstrainedY(P,level+1)); // The Initial Y Value
                pushStack(P, getFirstConstrainedX(P,level+1)); // The Initial X Value

            }


        }

    }

    private byte getFirstConstrainedX(int P, int n)
    {
        return 0;
    }

    private byte getFirstConstrainedY(int P, int n)
    {
        return getMinYConstraint(P,n);
    }

    private void revertToPreviousLevelIfExists(int P)
    {
        while (!stackIsEmpty(P))
        {
            // Then there is another level.
            byte testX = popStack(P);
            byte testY = popStack(P);

            // Swap this and check for another
            performSwap(P, testX, testY);

            if (pushNextIfInRegion(P, testX, testY))
                return;


        }
    }

    private boolean pushNextIfInRegion(int P, byte prevX, byte prevY)
    {
        int level = stackSize(P) / STACK_SPACE_FOR_EACH_LEVEL;

        if (prevX < MAX_POSSIBLE_WIDTH - 2)
        {
            prevX++;
            pushStack(P, prevY);
            pushStack(P, prevX);
            return true;

        }
        else if (prevY < getMaxYConstraint(P, level) -1)
        {
            prevY++;
            prevX=0;
            pushStack(P, prevY);
            pushStack(P, prevX);
            return true;

        }
        else
            return false;
    }


    private void checkPow(int N, int P, int numOfMoves, float rank)
    {
        if ((getCurrentScore(N)) < rank / numOfMoves)
        {
            dumpStackMoves(N,P);
            poolOfBestMoveMoves[N] = (byte)numOfMoves;
            poolOfBestMoveScore[N] = rank / numOfMoves;

        }
    }

    private void dumpStackMoves(int N, int P)
    {
        for (int i = 0; i < MAX_POSSIBLE_DEPTH; i++)
        {
            byte yMove = peekStack(P, i*2);
            byte xMove = peekStack(P, (i*2) + 1);

            // place into moves
            poolOfBestMoveInfo[(N*2*TOTAL_DEPTH)+((i+LEVELS_OF_DIVERGENCE)*2)] = xMove;

            poolOfBestMoveInfo[(N*2*TOTAL_DEPTH)+((i+LEVELS_OF_DIVERGENCE)*2) + 1] = yMove;
        }
    }

    private float getCurrentScore(int N)
    {
        return poolOfBestMoveScore[N];
    }



    /**
     * General Ops
     */

    private byte getMaxYConstraint(int P, int n)
    {
        return localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH + 2*n + 1];
    }

    private byte getMinYConstraint(int P, int n)
    {
        return localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH + 2*n];
    }

    private void copyConstraint(int P, int inN, int outN)
    {
        localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH + 2*outN] = getMinYConstraint(P, inN);
        localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH + 2*outN + 1] = getMaxYConstraint(P, inN);
    }

    public byte INITIAL_Y_CONSTRAINT = MAX_POSSIBLE_HEIGHT;
    public byte INITIAL_Y_CONSTRAINT_MIN = 0;
    private void initialiseConstraint(int N, int P)
    {
        localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH] = INITIAL_Y_CONSTRAINT_MIN;
        localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH+1] = INITIAL_Y_CONSTRAINT;
    }

    // Constrains so that each move constrains to a max height of 9.
    private void pushConstraint(int P, int n, int y)
    {
        int tmpymin = 0;
        int tmpymax = 0;

        if (y > 4)
            tmpymin = (y - 4);
        else
            tmpymin = 0;

        if (y > 6)
            tmpymax = MAX_POSSIBLE_HEIGHT;
        else
            tmpymax =( y + 6);

        if (tmpymax < localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH + 2*n + 1])
            localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH + 2*n + 1] = (byte)(tmpymax-1);
        if (tmpymin > localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH + 2*n])
            localyConstraintBuffer[P*2*MAX_POSSIBLE_DEPTH + 2*n] = (byte)tmpymin;


    }

    /**
     * Assumes pieces can swap.
     * @param x
     * @param y
     */
    public void performSwapOnSource(int x, int y)
    {
        byte tmp = decodeOriginalPiece(x,y);

        encodeOriginalPiece(x,y, decodeOriginalPiece(x+1,y));

        encodeOriginalPiece(x+1,y, tmp);
    }

    /**
     * Assumes pieces can swap.
     * @param x
     * @param y
     */
    private void performSwap(int P, int x, int y)
    {
        int tmp = elementAt(P,x,y);

        encodeSourcePiece(P,x,y, elementAt(P,x+1,y));

        encodeSourcePiece(P,x+1,y, tmp);
    }

    /**
     * Encodes 2d pieces array to 1d array.
     * TO decode use INDEX%BILGE_BOARD_SIZE_X to get the X index for the 2d array,
     * and INDEX/BILGE_BOARD_SIZE_X to get y index for the 2d array.
     * @param
     */
    private void encodeSourcePiece(int P, int x, int y, int value)
    {
        localPiecesBuffer[(BOARD_SIZE*P) + (MAX_POSSIBLE_WIDTH*y)+x] = (byte) value;
    }

    /**
     * Decodes element from 1d array.
     * @param x
     * @param y
     * @return
     */
    private int decodeSourcePiece(int P, int x, int y)
    {
        return localPiecesBuffer[(BOARD_SIZE*P) + (MAX_POSSIBLE_WIDTH*y)+x];
    }

    /**
     * Ranks the board assuming the coords would cause a pop.
     *
     * @param x
     * @param y
     */
    private float rankBoard(int P, int x, int y)
    {
        float score = 0;

        int counts = 0;

        boolean fiveRow = false;


        // Traverse around left piece.
        int yRow = retrievePiecesAbove(P, x,y);
        yRow += retrievePiecesBelow(P, x, y);


        if (yRow >= 2)
        {
            counts++;
            score += yRow;
        }

        if (yRow > 3)
            fiveRow = true;


        int xRow = retrievePiecesLeft(P,x,y);

        if (xRow >= 2)
        {
            counts++;
            score += xRow;
        }


        // Traverse around right piece.

        yRow = retrievePiecesAbove(P, x+1,y);
        yRow += retrievePiecesBelow(P, x+1, y);


        xRow = retrievePiecesRight(P,x+1,y);

        if (yRow >= 2)
        {
            counts++;
            score += yRow;
        }

        if (yRow > 3)
            fiveRow = true;

        if (xRow >= 2)
        {
            counts++;
            score += xRow;
        }



        float yRatio = ((y+1)/MAX_POSSIBLE_HEIGHT);

        if (counts == 1)
        {
            yRatio = yRatio + .45f;
            score = score * yRatio;
        }
        else if (counts == 2)
        {
            yRatio = yRatio + .7f;
            score = pow(score,1.1f) * yRatio;
        }
        else if (counts == 3)
        {
            yRatio = yRatio + 1.3f;
            score = pow(score,1.3f) * yRatio;
        }
        else if (counts >= 4 && fiveRow)
        {
            yRatio = yRatio + 24f;
            score = pow(score,1.7f) * yRatio;
        }
        else if (counts >= 4 )
        {
            yRatio = yRatio +20f;
            score = pow(score,2f) * yRatio;
        }



        return (score);

    }



    private int retrievePiecesBelow(int P, int x, int y)
    {
        int startElement = elementAt(P,x,y);

        int yOffset = 1;


        while (y-yOffset >= 0 && yOffset < 3)
        {
            if (elementAt(P,x,y-yOffset) == startElement)
            {
                yOffset++;
            }
            else
            {
                break;
            }
        }

        return (yOffset-1);
    }


    private int retrievePiecesAbove(int P, int x, int y)
    {
        int startElement = elementAt(P,x,y);

        int yOffset = 1;

        while (y+yOffset < MAX_POSSIBLE_HEIGHT && yOffset < 3)
        {
            if (elementAt(P,x,y+yOffset) == startElement)
            {
                yOffset++;
            }
            else
            {
                break;
            }
        }



        return (yOffset-1);
    }

    private int retrievePiecesLeft(int P, int x, int y)
    {
        int startElement = elementAt(P,x,y);

        int xOffset = 1;

        int counts = 0;

        while (x-xOffset >= 0 && xOffset < 3)
        {
            if (elementAt(P,x-xOffset,y) == startElement)
            {
                counts++;
                xOffset++;
            }
            else
            {
                break;
            }
        }

        return counts;
    }

    private int retrievePiecesRight(int P, int x, int y)
    {
        int startElement = elementAt(P,x,y);

        int xOffset = 1;

        int counts = 0;

        while (x+xOffset < MAX_POSSIBLE_WIDTH && xOffset < 3)
        {
            if (elementAt(P,x+xOffset,y)  == startElement)
            {
                counts++;
                xOffset++;
            }
            else
            {
                break;
            }
        }

        return counts;
    }

    private int retrievePiecesBelowAlt(int P, int x, int y)
    {
        int startElement = elementAt(P,x,y);

        int yOffset = 1;


        while (y-yOffset >= 0)
        {
            if (elementAt(P,x,y-yOffset) == startElement)
            {
                yOffset++;
            }
            else
            {
                break;
            }
        }

        return (yOffset-1);
    }


    private int retrievePiecesAboveAlt(int P, int x, int y)
    {
        int startElement = elementAt(P,x,y);

        int yOffset = 1;

        while (y+yOffset < MAX_POSSIBLE_HEIGHT)
        {
            if (elementAt(P,x,y+yOffset) == startElement)
            {
                yOffset++;
            }
            else
            {
                break;
            }
        }



        return (yOffset-1);
    }

    private int retrievePiecesLeftAlt(int P, int x, int y)
    {
        int startElement = elementAt(P,x,y);

        int xOffset = 1;

        int counts = 0;

        while (x-xOffset >= 0)
        {
            if (elementAt(P,x-xOffset,y) == startElement)
            {
                counts++;
                xOffset++;
            }
            else
            {
                break;
            }
        }

        return counts;
    }

    private int retrievePiecesRightAlt(int P, int x, int y)
    {
        int startElement = elementAt(P,x,y);

        int xOffset = 1;

        int counts = 0;

        while (x+xOffset < MAX_POSSIBLE_WIDTH)
        {
            if (elementAt(P,x+xOffset,y)  == startElement)
            {
                counts++;
                xOffset++;
            }
            else
            {
                break;
            }
        }

        return counts;
    }



    /**
     * Retrieval Ops
     */

    // Retrieves element from board N.
    private int elementAt(int P, int x, int y)
    {
        return decodeSourcePiece(P, x, y);
    }

    /**
     * Stack operations
     */
    private boolean stackIsEmpty(int P)
    {
        return stackSize(P) <= 0;
    }


    private int stackSize(int P)
    {
        return localstackSize[P];
    }

    private byte peekStack(int P, int n)
    {
        return localStack[(P*STACK_SIZE)+n];
    }

    private byte popStack(int P)
    {
        localstackSize[P]--;
        return localStack[(P*STACK_SIZE)+(localstackSize[P])];
    }

    private void pushStack(int P, byte n)
    {
        localStack[(P*STACK_SIZE)+(localstackSize[P])] = n;
        localstackSize[P]++;

    }


    /**
     * Simple helper operations
     */

    private boolean canSwap(int left, int right)
    {
        // false if both piecse are the same
        if (left == right)
        {
            return false;
        }



        // False if one of the pieces is not between 0 and 6.
        // Anything higher is a special, jelly, etc.
        if (isValidPiece(left) && isValidPiece(right))
            return true;

        return false;
    }

    private boolean isValidPiece(int index)
    {
        if (index > 6)
            return false;
        else
            return true;
    }

    private boolean isCrab(int index)
    {
        return index == 8;
    }

    private boolean isJellyFish(int index)
    {
        return index == 9;
    }

    private boolean isPufferFish(int index)
    {
        return index == 7;
    }


    /**
     * Board solution logic
     */


    /**
     * Skips looping over entire board. More efficient.
     * @param P - P index
     * @param x - swap source x
     * @param y - swap source y
     * @return number of steps (breaks)
     */
    private int breakIntoCheckAll(int P, int x, int y)
    {
        //@TODO actaully make it more efficient.

        return checkToAllBreaks(P);

    }

    private int jellyIntoCheckAll(int P, int x, int y, boolean rightSide)
    {
        // Jelly is on the right
        if (rightSide)
        {
            destroyAllOfElement(P, elementAt(P,x,y));
            encodeSourcePiece(P,x+1,y,EMPTY_PIECE);
        }
        else // Jelly is on the left
        {
            destroyAllOfElement(P, elementAt(P,x+1,y));
            encodeSourcePiece(P,x,y,EMPTY_PIECE);
        }

        stepForward(P);

        return checkToAllBreaks(P) + 1;
    }

    private int pufferIntoCheckAll(int P, int x, int y, boolean rightSide)
    {
        if (rightSide)
            huffAt(P,x+1,y);
        else
            huffAt(P, x,y);

        stepForward(P);

        return checkToAllBreaks(P) + 1;
    }

    private void huffAt(int P, int x, int y)
    {

        for (int xOffset = 0; xOffset < 3; xOffset++)
        {
            for (int yOffset = 0; yOffset < 3; yOffset++)
            {
                int iX = x-1+xOffset;
                int iY = y-1+yOffset;

                if (iX >= 0 && iX < MAX_POSSIBLE_WIDTH && iY >= 0 && iY < MAX_POSSIBLE_HEIGHT)
                {
                    encodeSourcePiece(P,iX,iY,EMPTY_PIECE);
                }

            }
        }
    }

    private void destroyAllOfElement(int P, int element)
    {
        for (int x = 0; x < MAX_POSSIBLE_WIDTH-1; x++)
        {
            for (int y = 0; y < MAX_POSSIBLE_HEIGHT; y++)
            {
                if (elementAt(P,x,y) == element)
                {
                    encodeSourcePiece(P,x,y,EMPTY_PIECE);
                }

            }
        }
    }

    /**
     * Checks the board for solutions and swaps them with empty pieces if solved.
     *
     * @param P - P index
     * @return - number of steps (breaks)
     */
    private int checkToAllBreaks(int P)
    {

        int steps = 0;
        while (checkToBreak(P))
        {
            steps ++;
        }


        return steps;
    }

    /**
     * Checks the board for solutions and swaps them with empty pieces if solved.
     *
     * @param P - P index
     * @return - whether or not there was a break
     */
    private boolean checkToBreak(int P)
    {
        boolean broken = false;

        for (int x = 0; x < MAX_POSSIBLE_WIDTH-1; x++)
        {
            for (int y = 0; y < MAX_POSSIBLE_HEIGHT; y++)
            {
                if (isValidPiece(elementAt(P,x,y)) && breakCheck(P, x, y))
                {
                    broken = true;
                }


            }
        }

        if (broken)
            stepForward(P);

        return broken;
    }

    private void stepForward(int P)
    {
        // Step board forward and check for crabs, step again if crabs
        while (step(P) && crabCheck(P) > 0);
    }

    private void breakPufferfish(int P, int x, int y)
    {
        //@TODO
    }

    private int crabCheck(int P)
    {
        int crab_count = 0;
        for (int x = 0; x < MAX_POSSIBLE_WIDTH-1; x++)
        {
            for (int y = waterLevel + 1; y < MAX_POSSIBLE_HEIGHT; y++)
            {
                if (isCrab(elementAt(P,x,y)))
                {
                    encodeSourcePiece(P,x,y, EMPTY_PIECE);
                    crab_count++;
                }

            }
        }
        return crab_count;
    }

    private boolean breakCheck(int P, int x, int y)
    {

        boolean broke = false;


        // Traverse around left piece.
        int yRowA = retrievePiecesAboveAlt(P, x,y);
        int yRowB = retrievePiecesBelowAlt(P, x,y);



        int xRowL = retrievePiecesLeftAlt(P,x,y);
        int xRowR = retrievePiecesRightAlt(P,x,y);


        if (yRowA + yRowB > 1 || xRowL + xRowR > 1)
        {
            encodeSourcePiece(P, x,y, EMPTY_PIECE);
            broke = true;

        }

        if (yRowA + yRowB >= 2)
        {
            breakUp(P,x,y,yRowA);
            breakBelow(P,x,y,yRowB);
        }

        if (xRowL + xRowR >= 2)
        {
            breakRight(P,x,y,xRowR);
            breakLeft(P,x,y,xRowL);
        }

        return broke;

    }

    private void breakLeft(int P, int x, int y, int counts)
    {

        int xOffset = 1;

        while (x-xOffset >= 0 && xOffset <= counts)
        {
            encodeSourcePiece(P,x-xOffset,y, EMPTY_PIECE);
            xOffset++;
        }

    }

    private void breakRight(int P, int x, int y, int counts)
    {

        int xOffset = 1;

        while (x+xOffset < MAX_POSSIBLE_WIDTH && xOffset <= counts)
        {
            encodeSourcePiece(P,x+xOffset,y, EMPTY_PIECE);
            xOffset++;
        }

    }

    private void breakBelow(int P, int x, int y, int counts)
    {
        int yOffset = 1;


        while (y-yOffset >= 0 && yOffset <= counts)
        {
            encodeSourcePiece(P,x,y-yOffset, EMPTY_PIECE);
            yOffset++;
        }

    }


    private void breakUp(int P, int x, int y, int counts)
    {

        int yOffset = 1;


        while (y+yOffset < MAX_POSSIBLE_HEIGHT && yOffset <= counts)
        {
            encodeSourcePiece(P,x,y+yOffset, EMPTY_PIECE);
            yOffset++;
        }

    }

    /**
     * Steps the game forward, places all empty pieces at the bottom.
     * @param P
     * @return - whether or not there was a step
     */
    private boolean step(int P)
    {
        boolean step = false;
        for (int x = 0; x < MAX_POSSIBLE_WIDTH; x++)
        {
            if (stepX(P, x))
                step = true;
        }
        return step;
    }

    private boolean stepX(int P, int x)
    {
        boolean stepped = false;
        int a = MAX_POSSIBLE_HEIGHT - 1;
        int b = MAX_POSSIBLE_HEIGHT - 1;
        while (a >= 0) {
            int elm = elementAt(P,x,a);
            if (elm != EMPTY_PIECE) {
                encodeSourcePiece(P,x,b--,elm);
            }
            a--;
        }
        if (b >= 0)
        {
            stepped = true;
            while (b >= 0) encodeSourcePiece(P,x,b--,EMPTY_PIECE);
        }
        return stepped;


    }

    private void logBoard(int P)
    {
        StringBuilder sb = new StringBuilder();
        for (int y = MAX_POSSIBLE_HEIGHT-1; y >= 0; y--)
        {
            for (int x = 0; x < MAX_POSSIBLE_WIDTH; x++)
            {
                sb.append(decodeSourcePiece(P,x,y));
                sb.append(" ");
            }
            System.out.println(y + ": " + sb.toString());
            sb.setLength(0);
        }
    }


}
