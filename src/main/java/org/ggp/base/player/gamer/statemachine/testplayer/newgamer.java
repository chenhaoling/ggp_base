package org.ggp.base.player.gamer.statemachine.testplayer;

import javafx.util.Pair;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class newgamer extends SampleGamer {
    private Boolean isFirst = true;
    private Pair<Integer, Integer> center = null;
    private List channel = Collections.synchronizedList(Arrays.asList(new Object[4]));
    private Role me = null;
    private Role opponent = null;
    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        StateMachine theMachine = getStateMachine();
        channel.set(0, 0);
        //获取角色
        me = getRole();
        for (Role i:theMachine.getRoleIndices().keySet()) {
            if(!i.equals(me)){
                opponent = i;
                break;
            }
        }
        //获取先后手
        this.isFirst = (theMachine.getRoleIndices().get(me) == 0);

        Role r = null;
        //获取中心点位置
        if (isFirst) {
            r = me;
        }
        else {
            r = opponent;
        }
        List<Move> moves = theMachine.getLegalMoves(getCurrentState(), r);
        int max = 0;
        for (Move i:moves) {
            String move_gdl = i.toString();
            List<String> tmp = Arrays.asList(move_gdl.split(" "));
            int x = Integer.parseInt(tmp.get(2));
            int y = Integer.parseInt(tmp.get(3));

            Integer max_x_y = Math.max(x, y);
            if(max < max_x_y) {
                max = max_x_y;
            }
        }

        if(max % 2 == 1) {
            center = new Pair<>(max / 2 + 1, max / 2 + 1);
        }

        MonteCarloTreeSearchThread thread = new MonteCarloTreeSearchThread(channel, new TreeNode(moves.size(), isFirst, getCurrentState()), theMachine, me, opponent, getCurrentState(), moves);
        new Thread(thread).start();
        super.stateMachineMetaGame(timeout);
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        //获取游戏状态机
        StateMachine theMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 5000;

        //获取当前状态所有可行的动作
        List<Move> moves = theMachine.getLegalMoves(getCurrentState(), me);
        Move selection = moves.get(0);

        //判断可行的动作数量是否大于1 小于1直接就是他了
        if(moves.size() > 1) {
            //----------启发式------------------可能得花费一点时间 不能停
            if(isFirst) {
                //放中间
                for (Move m:
                        moves) {
                    List<String> tmp = Arrays.asList(m.toString().split(" "));
                    if(tmp.get(2).equals(center.getKey().toString()) && tmp.get(3).equals(center.getValue().toString())) {
                        MachineState nextState = theMachine.getNextState(getCurrentState(), theMachine.getRandomJointMove(getCurrentState(), me, m));
                        synchronized (channel) {
                            try {
                                channel.wait(finishBy - System.currentTimeMillis());
                            }
                            catch (Exception e) {
                                System.out.println("现在是newgame 1里e");
                                e.printStackTrace();
                            }
                            System.out.println("现在是newgame 1里");
                            channel.set(0, 1);
                            channel.set(1, nextState);
                            channel.set(2, theMachine.getLegalMoves(nextState, opponent));
                            channel.notifyAll();
                        }
                        long stop = System.currentTimeMillis();
                        notifyObservers(new GamerSelectedMoveEvent(moves, m, stop - start));
                        return m;
                    }
                }
                isFirst = false;
            }

            //----------蒙特卡洛树搜索------------------
            //更新节点 考虑到启发式也有可能花费大量时间
            synchronized (channel) {
                try {
                    channel.wait(finishBy - System.currentTimeMillis());
                }
                catch (Exception e) {
                    System.out.println("现在是newgame 2里e");
                    e.printStackTrace();
                }
                System.out.println("现在是newgame 2里");
                channel.set(0, 1);
                channel.set(1, getCurrentState());
                channel.set(2, moves);
                channel.notifyAll();
            }

            //如果存在一步获胜，则选取这一步
            //防止对方下一步能赢
            {
                moves = new ArrayList<Move>(moves);
                Map<Move, List<MachineState>> move_map = theMachine.getNextStates(getCurrentState(), getRole());
                for (Move i :
                        move_map.keySet()) {
                    for (MachineState ms :
                            move_map.get(i)) {
                        if (theMachine.isTerminal(ms)) {
                            if (theMachine.getGoal(ms, getRole()) == 100) {
                                long stop = System.currentTimeMillis();
                                notifyObservers(new GamerSelectedMoveEvent(moves, i, stop - start));
                                return i;
                            }
                        }
                        boolean forcedLoss = false;
                        for (List<Move> jointMove : theMachine.getLegalJointMoves(ms)) {
                            MachineState nextNextState = theMachine.getNextState(ms, jointMove);
                            if (theMachine.isTerminal(nextNextState)) {
                                if (theMachine.getGoal(nextNextState, getRole()) == 0) {
                                    forcedLoss = true;
                                    break;
                                }
                            }
                        }
                        if (forcedLoss) {
                            moves.remove(i);
                            break;
                        }
                    }
                }
            }

            if (moves.size() > 1) {
                //----------蒙特卡洛树搜索------------------
                //更新节点
                synchronized (channel) {
                    try {
                        channel.wait(finishBy - System.currentTimeMillis());
                    }
                    catch (Exception e) {
                        System.out.println("现在是newgame 3里e");
                        e.printStackTrace();
                    }
                    System.out.println("现在是newgame 3里");
                    channel.set(0, 1);
                    channel.set(1, getCurrentState());
                    channel.set(2, moves);
                    channel.notifyAll();
                }

                while (System.currentTimeMillis() <= finishBy);

                synchronized (channel) {
                    try {
                        channel.wait(finishBy - System.currentTimeMillis());
                    }
                    catch (Exception e) {
                        System.out.println("现在是newgame 4里e");
                        e.printStackTrace();
                    }
                    System.out.println("现在是newgame 4里");
                    channel.set(0, 2);
                    channel.notifyAll();
                }

                synchronized (channel) {
                    try {
                        channel.wait(finishBy - System.currentTimeMillis());
                    }
                    catch (Exception e) {
                        System.out.println("现在是newgame 5里e");
                        e.printStackTrace();
                    }
                    System.out.println("现在是newgame 5里");
                    selection = (Move) channel.get(3);
                    channel.set(3, null);
                    channel.notifyAll();
                }
            }
            else if(moves.size() == 1){
                selection = moves.get(0);
            }
            else {
                moves = theMachine.getLegalMoves(getCurrentState(), getRole());
                selection = moves.get(ThreadLocalRandom.current().nextInt(moves.size()));
            }
        }
        if(selection == null) {
            moves = theMachine.getLegalMoves(getCurrentState(), getRole());
            selection = moves.get(ThreadLocalRandom.current().nextInt(moves.size()));
            System.out.println("selection is null random select " + selection);
        }

        long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(theMachine.getLegalMoves(getCurrentState(), getRole()), selection, stop - start));
        return selection;
    }
}

