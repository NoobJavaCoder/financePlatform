package com.sunlights.customer.dal.impl;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.sunlights.common.AppConst;
import com.sunlights.common.DictConst;
import com.sunlights.common.dal.EntityBaseDao;
import com.sunlights.common.exceptions.ConverterException;
import com.sunlights.common.utils.ConverterUtil;
import com.sunlights.common.utils.DBHelper;
import com.sunlights.customer.dal.CustomerDao;
import com.sunlights.customer.vo.CustomerVo;
import models.Customer;
import models.CustomerGesture;
import models.CustomerSession;
import models.ShuMiAccount;
import org.apache.commons.lang3.StringUtils;
import play.Logger;

import javax.persistence.Query;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Project: fsp</p>
 * <p>Title: CustomerManageDaoImpl.java</p>
 * <p>Description: </p>
 * <p>Copyright (c) 2014 Sunlights.cc</p>
 * <p>All Rights Reserved.</p>
 *
 * @author <a href="mailto:jiaming.wang@sunlights.cc">wangJiaMing</a>
 */

public class CustomerDaoImpl extends EntityBaseDao implements CustomerDao {
    public String getCustomerIdSeq() {
        Query query = em.createNativeQuery("SELECT nextval('cust_seq')");
        String cust_seq = query.getSingleResult().toString();
        return Strings.padStart(cust_seq, 10, '0');
    }

    //Customer
    @Override
    public Customer getCustomerByMobile(String mobile) {
        List<Customer> list = findBy(Customer.class, "mobile", mobile);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public Customer getCustomerByCustomerId(String customerId) {
        List<Customer> list = findBy(Customer.class, "customerId", customerId);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public Customer updateCustomer(Customer customer) {
        return update(customer);
    }

    @Override
    public Customer saveCustomer(Customer customer) {
        return create(customer);
    }

    public CustomerVo getCustomerVoByPhoneNo(String mobilePhoneNo, String deviceNo) {
        String sql = " select c.mobile,c.real_name,c.nick_name,c.email,c.identity_number," +
                " case when c.identity_typer = :identityTyper and c.identity_number is not null THEN '1' ELSE '0' END as certify," +
                " c.customer_id" +
                " from c_customer c" +
                " where c.mobile = :mobilePhoneNo";
        Logger.debug(sql);

        Query query = em.createNativeQuery(sql);
        query.setParameter("identityTyper", DictConst.CERTIFICATE_TYPE_1);
        query.setParameter("mobilePhoneNo", mobilePhoneNo);
        List<Object[]> list = query.getResultList();
        CustomerVo customerVo = transCustomerVo(list);
        if (customerVo != null && StringUtils.isNotEmpty(deviceNo)) {//手势设置查询
            String deviceNoSql = "select cg from CustomerGesture cg where cg.customerId = :customerId and cg.deviceNo = :deviceNo order by cg.updateTime desc";
            query = em.createQuery(deviceNoSql, CustomerGesture.class);
            query.setParameter("customerId", customerVo.getCustomerId());
            query.setParameter("deviceNo", deviceNo);
            List<CustomerGesture> customerGestureList = query.getResultList();
            if (!customerGestureList.isEmpty()) {
                customerVo.setGestureOpened(AppConst.STATUS_VALID.equals(customerGestureList.get(0).getStatus()) ? "1" : "0");
                customerVo.setGestureSetted("1");
            }
        }

        return customerVo;
    }

    public CustomerVo getCustomerVoByIdCardNo(String idCardNo, String realName) {
        String sql = " select c.mobile,c.real_name,c.nick_name,c.email,c.identity_number," +
                " case when c.identity_typer = :identityTyper and c.identity_number is not null THEN '1' ELSE '0' END as certify," +
                "  c.customer_id " +
                " from    c_customer c" +
                " where   c.real_name = :realName" +
                " and     c.identity_typer = :identityTyper" +
                " and     c.identity_number = :idCardNo";

        Logger.debug(sql);

        Query query = em.createNativeQuery(sql);
        query.setParameter("identityTyper", DictConst.CERTIFICATE_TYPE_1);
        query.setParameter("idCardNo", idCardNo);
        query.setParameter("realName", realName);
        List list = query.getResultList();

        CustomerVo customerVo = transCustomerVo(list);
        if (customerVo != null) {
            customerVo.setGestureOpened("0");
            customerVo.setGestureSetted("0");
        }

        return customerVo;
    }

    public CustomerVo getCustomerVoByAuthenticationMobile(String mobile) {
        String sql = " select c.mobile,c.identity_number,c.real_name,c.email,c.customer_id,c.authentication_id" +
                "  from c_customer c, c_authentication a " +
                " where c.authentication_id = a.id " +
                "  and  a.mobile = :mobile";
        Logger.debug(sql);

        Query query = em.createNativeQuery(sql);
        query.setParameter("mobile", mobile);
        List<Object[]> list = query.getResultList();
        if (list.isEmpty()) {
            return null;
        }

        String keys = "mobilePhoneNo,idCardNo,userName,email,customerId,authenticationId";
        List<CustomerVo> customerVos = ConverterUtil.convert(keys, list, CustomerVo.class);

        return customerVos.get(0);
    }

    public CustomerVo getCustomerVoBySocial(String socialType, String socialNo){
        String sql = " select c.mobile,c.identity_number,c.real_name,c.email,c.customer_id,c.authentication_id" +
                "  from c_customer c, c_authentication a " +
                " where c.authentication_id = a.id ";

        if (DictConst.SOCIAL_TYPE_WECHAT.equals(socialType)) {
            sql += " and weixin = :socialNo";
        }
        Logger.debug(sql);

        Query query = em.createNativeQuery(sql);
        query.setParameter("socialNo", socialNo);
        List<Object[]> list = query.getResultList();
        if (list.isEmpty()) {
            return null;
        }

        String keys = "mobilePhoneNo,idCardNo,userName,email,customerId,authenticationId";
        List<CustomerVo> customerVos = ConverterUtil.convert(keys, list, CustomerVo.class);

        return customerVos.get(0);
    }


    private CustomerVo transCustomerVo(List<Object[]> list) {
        if (list.isEmpty()) {
            return null;
        }

        String keys = "mobilePhoneNo,userName,nickName,email,idCardNo,certify,customerId";
        List<CustomerVo> customerVos = ConverterUtil.convert(keys, list, CustomerVo.class);
        CustomerVo customerVo = customerVos.get(0);
        findShuMiAccount(customerVo);

        if (customerVo.getMobilePhoneNo() != null) {
            customerVo.setMobileDisplayNo(customerVo.getMobilePhoneNo().substring(0, 3) + "****" + customerVo.getMobilePhoneNo().substring(7));
        }
        if (AppConst.VALID_CERTIFY.equals(customerVo.getCertify())) {
            customerVo.setIdCardNo(customerVo.getIdCardNo().substring(0, 6) + "******" + customerVo.getIdCardNo().substring(14));
            customerVo.setUserName("*" + customerVo.getUserName().substring(1));
        }
        return customerVo;
    }

    private void findShuMiAccount(CustomerVo customerVo) {
        Query query = createNameQuery("findShuMiAccount", customerVo.getCustomerId());
        List<ShuMiAccount> list = query.getResultList();
        if (list.isEmpty()) {
            return;
        }
        try {
            ConverterUtil.fromEntity(customerVo, list.get(0));
        } catch (ConverterException e) {
            e.printStackTrace();
        }

    }


    //CustomerSession
    public CustomerSession findCustomerSessionByToken(String token, Timestamp nMin) {
        StringBuffer sb = new StringBuffer();
        sb.append("select c FROM CustomerSession c ");
        sb.append("where c.status = 'Y' ");
        sb.append(" /~ and c.token = {token} ~/ ");
        sb.append(" /~ and c.updateTime >= {nMin} ~/ ");

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("EQS_token", token);
        params.put("GED_nMin", new Date(nMin.getTime()));

        List<CustomerSession> list = findByMap(sb.toString(), params);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public CustomerSession saveCustomerSession(CustomerSession customerSession) {
        return create(customerSession);
    }

    @Override
    public CustomerSession updateCustomerSession(CustomerSession customerSession) {
        return update(customerSession);
    }

    public CustomerSession findCustomerSessionByCustomerId(String customerId, String deviceNo) {
        Query query = createNameQuery("findCSByCustomerId", customerId, deviceNo);
        List<CustomerSession> list = query.getResultList();
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }


    //CustomerGesture
    @Override
    public CustomerGesture saveCustomerGesture(CustomerGesture customerGesture) {
        return create(customerGesture);
    }

    @Override
    public CustomerGesture updateCustomerGesture(CustomerGesture customerGesture) {
        return update(customerGesture);
    }

    public CustomerGesture findCustomerGestureByDeviceNo(String customerId, String deviceNo) {
        Query query = createNameQuery("findCGByCustomerId", customerId, deviceNo);
        List<CustomerGesture> list = query.getResultList();
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public Customer findRecommenderInfo(String customerId) {
        if (StringUtils.isEmpty(customerId)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        String columns = "c2.customer_id  ";
        sb.append("select ").append(columns)
                .append("from c_customer c1 join c_customer c2 on c2.mobile = c1.recommend_phone where 1 = 1 and  /~c1.customer_id = {customerId}~/");

        Map<String, Object> filterMap = Maps.newHashMapWithExpectedSize(5);

        filterMap.put("EQS_customerId", customerId);
        List<String> resultRows = createNativeQueryByMap(sb.toString(), filterMap).getResultList();
        if (resultRows == null || resultRows.isEmpty()) {
            return null;
        }
        Customer customer = new Customer();
        customer.setCustomerId(resultRows.get(0));
        return customer;
    }

    @Override
    public boolean validateHasFirstPurchase(String customerId) {
        String sql = "select count(1) from t_trade t where t.cust_id = :customerId and t.create_time > :currentTime";
        Query query = em.createNativeQuery(sql);
        query.setParameter("customerId", customerId);
        query.setParameter("currentTime", DBHelper.afterMinutes(DBHelper.getCurrentTime(), 60));//考虑到调用时间延迟，给1小时移除当前操作交易影响
        int count = Integer.valueOf(query.getSingleResult().toString());
        if (count == 0) {
            return false;
        }

        return true;
    }
}
