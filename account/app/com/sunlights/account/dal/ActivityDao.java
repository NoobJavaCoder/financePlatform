package com.sunlights.account.dal;

import com.sunlights.common.vo.PageVo;
import models.Activity;

import java.util.List;

/**
 * Created by tangweiqun on 2014/11/17.
 */
public interface ActivityDao {

    public List<Activity> getActivityVos(PageVo pageVo);

    /**
     * 根据活动的应用场景查处这个场景下的所有活动
     * @param scene 应用场景
     * @return
     */
    public List<Activity> getActivityByScene(String scene);

}
