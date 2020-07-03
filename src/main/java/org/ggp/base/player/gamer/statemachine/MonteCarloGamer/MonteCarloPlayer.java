package org.ggp.base.player.gamer.statemachine.MonteCarloGamer;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MonteCarloPlayer extends SampleGamer {
    private Role me = null;
    private Role opponent = null;

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        StateMachine theMachine = getStateMachine();
        //获取角色
        me = getRole();
        for (Role i :
                theMachine.getRoleIndices().keySet()) {
            if (!i.equals(me)) {
                opponent = i;
                break;
            }
        }
        super.stateMachineMetaGame(timeout);
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        //获取游戏状态机
        StateMachine theMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        //获取当前状态所有可行的动作
        List<Move> moves = theMachine.getLegalMoves(getCurrentState(), getRole());
        Move selection = moves.get(0);
        if (moves.size() > 1) {
            //----------蒙特卡洛树搜索------------------
            //创建根结点
            TreeNode Root = new TreeNode(moves.size());
            List<Move> tmp_moves = new ArrayList<>();
            for(Move t : moves) {
                tmp_moves.add(t);
            }
            int loop_i = 0;
            //只要还在截止时间内
            while (System.currentTimeMillis() <= finishBy) {
                loop_i++;
                int score;
                //tmp节点一开始指向根结点
                TreeNode tmp = Root;
                //获取游戏当前局面状态
                MachineState state = getCurrentState();

                //创建反向传播列表 用于最后反向传播是更新对应的w_k值
                List<TreeNode> backList = new ArrayList<>();

                //加入的节点n_k值加一
                tmp.setN_k(tmp.getN_k() + 1);
                backList.add(tmp);

                //只要满足选举的条件——该节点的孩子数等于他可有的合法孩子节点数 即所有孩子都至少走过一次
                while (tmp.getChild().size() == tmp.getLegalMoveNum()) {
                    //select
                    //选举一个最合适的孩子节点
                    tmp = tmp.checkBestChild();

                    //把该节点加进去 n_k值加一
                    tmp.setN_k(tmp.getN_k() + 1);
                    backList.add(tmp);

                    //状态转换
                    if (!tmp.getMax()) {
                        state = theMachine.getNextState(state, theMachine.getRandomJointMove(state, me, tmp.getMove()));
                    } else {
                        state = theMachine.getNextState(state, theMachine.getRandomJointMove(state, opponent, tmp.getMove()));
                    }
                    if (theMachine.isTerminal(state))
                        break;
                }
                //判断是否为终态
                if (!theMachine.isTerminal(state)) {
                    //expand
                    //扩展 判断是哪种节点要扩展
                    TreeNode expandNode = null;
                    Boolean thisMax = !tmp.getMax();

                    boolean hasNode = false;

                    //从当前状态获取自己的所有合法动作
                    if (tmp != Root) {
                        if (!thisMax) {
                            moves = theMachine.getLegalMoves(state, me);
                        } else {
                            moves = theMachine.getLegalMoves(state, opponent);
                        }
                    } else {
                        moves = tmp_moves;
                    }

                    //随机选一个
                    Move moveUnderConsideration = moves.get(ThreadLocalRandom.current().nextInt(moves.size()));

                    if (!thisMax) {
                        state = theMachine.getNextState(state, theMachine.getRandomJointMove(state, me, moveUnderConsideration));
                    } else {
                        state = theMachine.getNextState(state, theMachine.getRandomJointMove(state, opponent, moveUnderConsideration));
                    }

                    if(!theMachine.isTerminal(state)) {
                        if (!thisMax) {
                            moves = theMachine.getLegalMoves(state, opponent);
                        } else {
                            moves = theMachine.getLegalMoves(state, me);
                        }

                        //判断这一个点是不是已经被创建了 从tmp的child里找
                        for (TreeNode i :
                                tmp.getChild()) {
                            //如果找到一样的就直接模拟
                            if (i.getMove().equals(moveUnderConsideration)) {
                                expandNode = i;
                                hasNode = true;
                                break;
                            }
                        }

                        if (!hasNode) {
                            //创建扩展节点
                            expandNode = new TreeNode(moveUnderConsideration, moves.size(), thisMax);
                            //把子节点加进父节点child中
                            tmp.getChild().add(expandNode);
                        }


                        //把孩子节点加进反向传播列表中
                        backList.add(expandNode);
                        expandNode.setN_k(expandNode.getN_k() + 1);

                        //simulation
                        state = theMachine.performDepthCharge(state, depth);
                    }
                }

                score = theMachine.getGoal(state, me);
                //back 反向传播回溯结果
                for (TreeNode i :
                        backList) {
                    i.setW_k(i.getW_k() + score / 100.0);
                }
            }
            //时间不够 选择根结点的最好子节点
            selection = Root.checkBestChild().getMove();
            System.out.println("MCTS num of test:" + loop_i);
            System.out.println(selection);
        }

        long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(theMachine.getLegalMoves(getCurrentState(), getRole()), selection, stop - start));
        return selection;
    }

    private int[] depth = new int[1];
}
