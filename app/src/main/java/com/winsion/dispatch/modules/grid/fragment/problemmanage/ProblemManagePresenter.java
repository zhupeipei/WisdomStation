package com.winsion.dispatch.modules.grid.fragment.problemmanage;

import android.content.Context;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.winsion.dispatch.application.AppApplication;
import com.winsion.dispatch.common.biz.CommonBiz;
import com.winsion.dispatch.data.CacheDataSource;
import com.winsion.dispatch.data.NetDataSource;
import com.winsion.dispatch.data.constants.FieldKey;
import com.winsion.dispatch.data.constants.JoinKey;
import com.winsion.dispatch.data.constants.Mode;
import com.winsion.dispatch.data.constants.OpeCode;
import com.winsion.dispatch.data.constants.Urls;
import com.winsion.dispatch.data.constants.ViewName;
import com.winsion.dispatch.data.entity.OrderBy;
import com.winsion.dispatch.data.entity.ResponseForQueryData;
import com.winsion.dispatch.data.entity.WhereClause;
import com.winsion.dispatch.data.listener.ResponseListener;
import com.winsion.dispatch.modules.operation.constants.TaskType;
import com.winsion.dispatch.modules.operation.entity.JobEntity;
import com.winsion.dispatch.modules.operation.entity.JobParameter;
import com.winsion.dispatch.modules.operation.entity.TaskEntity;
import com.winsion.dispatch.utils.JsonUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by wyl on 2017/6/29
 */
public class ProblemManagePresenter implements ProblemManageContract.Presenter {
    private ProblemManageContract.View mView;
    private Context mContext;

    ProblemManagePresenter(ProblemManageContract.View view) {
        this.mView = view;
        this.mContext = view.getContext();
    }

    @Override
    public void start() {

    }

    @Override
    public void getProblemData() {
        if (AppApplication.TEST_MODE) {
            mView.getProblemDataSuccess(JsonUtils.getTestEntities(mContext, TaskEntity.class));
            return;
        }
        List<WhereClause> whereClauses = new ArrayList<>();

        WhereClause whereClause = new WhereClause();
        whereClause.setFields("monitorteamid");
        whereClause.setValueKey(CacheDataSource.getTeamId());
        whereClause.setJoinKey(JoinKey.AND);
        whereClause.setFieldKey(FieldKey.EQUALS);
        whereClauses.add(whereClause);

        WhereClause whereClause1 = new WhereClause();
        whereClause1.setFields("taktype");
        whereClause1.setValueKey(String.valueOf(TaskType.GRID));
        whereClause1.setJoinKey(JoinKey.OTHER);
        whereClause1.setFieldKey(FieldKey.EQUALS);
        whereClauses.add(whereClause1);

        List<OrderBy> orderByList = new ArrayList<>();
        OrderBy orderBy = new OrderBy();
        orderBy.setField("planstarttime");
        orderBy.setMode(Mode.ASC);
        orderByList.add(orderBy);

        NetDataSource.post(this, Urls.BASE_QUERY, whereClauses, orderByList, ViewName.TASK_INFO, 1,
                new ResponseListener<ResponseForQueryData<List<TaskEntity>>>() {
                    @Override
                    public ResponseForQueryData<List<TaskEntity>> convert(String jsonStr) {
                        Type type = new TypeReference<ResponseForQueryData<List<TaskEntity>>>() {
                        }.getType();
                        return JSON.parseObject(jsonStr, type);
                    }

                    @Override
                    public void onSuccess(ResponseForQueryData<List<TaskEntity>> result) {
                        mView.getProblemDataSuccess(result.getDataList());
                    }

                    @Override
                    public void onFailed(int errorCode, String errorInfo) {
                        mView.getProblemDataFailed(errorInfo);
                    }
                });
    }

    @Override
    public void confirm(TaskEntity taskEntity, int opeType) {
        if (AppApplication.TEST_MODE) {
            mView.confirmSuccess(taskEntity.getTasksid(), opeType);
            return;
        }
        // 先用taskId查询JobEntity在调用确认接口
        List<WhereClause> whereClauses = new ArrayList<>();

        WhereClause whereClause = new WhereClause();
        whereClause.setFieldKey(FieldKey.EQUALS);
        whereClause.setJoinKey(JoinKey.OTHER);
        whereClause.setValueKey(taskEntity.getTasksid());
        whereClause.setFields("tasksid");
        whereClauses.add(whereClause);

        NetDataSource.post(this, Urls.BASE_QUERY, whereClauses, null,
                ViewName.JOB_INFO, 1, new ResponseListener<ResponseForQueryData<List<JobEntity>>>() {
                    @Override
                    public ResponseForQueryData<List<JobEntity>> convert(String jsonStr) {
                        Type type = new TypeReference<ResponseForQueryData<List<JobEntity>>>() {
                        }.getType();
                        return JSON.parseObject(jsonStr, type);
                    }

                    @Override
                    public void onSuccess(ResponseForQueryData<List<JobEntity>> result) {
                        List<JobEntity> dataList = result.getDataList();
                        if (dataList != null && dataList.size() == 1) {
                            getJobInfoFinished(dataList.get(0), opeType);
                        } else {
                            mView.confirmFailed(taskEntity.getTasksid());
                        }
                    }

                    @Override
                    public void onFailed(int errorCode, String errorInfo) {
                        mView.confirmFailed(taskEntity.getTasksid());
                    }
                });
    }

    private void getJobInfoFinished(JobEntity jobDto, int opeType) {
        JobParameter jobParameter = new JobParameter();
        jobParameter.setUsersId(CacheDataSource.getUserId());
        jobParameter.setJobsId(jobDto.getJobsid());
        jobParameter.setSsId(CommonBiz.getBSSID(mContext));
        jobParameter.setTaskId(jobDto.getTasksid());
        jobParameter.setOpormotId(jobDto.getJoboperatorsid());
        jobParameter.setOpType(opeType);
        NetDataSource.post(this, Urls.JOb, jobParameter, OpeCode.TASK, new ResponseListener<String>() {
            @Override
            public String convert(String jsonStr) {
                return jsonStr;
            }

            @Override
            public void onSuccess(String result) {
                mView.confirmSuccess(jobDto.getTasksid(), opeType);
            }

            @Override
            public void onFailed(int errorCode, String errorInfo) {
                mView.confirmFailed(jobDto.getTasksid());
            }
        });
    }

    @Override
    public void exit() {
        NetDataSource.unSubscribe(this);
    }
}
