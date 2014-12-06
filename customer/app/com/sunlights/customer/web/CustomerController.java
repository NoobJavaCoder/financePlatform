package com.sunlights.customer.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunlights.common.AppConst;
import com.sunlights.common.MsgCode;
import com.sunlights.common.Severity;
import com.sunlights.common.service.VerifyCodeService;
import com.sunlights.common.utils.MessageUtil;
import com.sunlights.common.vo.Message;
import com.sunlights.common.vo.MessageVo;
import com.sunlights.customer.service.LoginService;
import com.sunlights.customer.service.impl.CustomerService;
import com.sunlights.customer.service.impl.LoginServiceImpl;
import com.sunlights.customer.vo.CustomerFormVo;
import com.sunlights.customer.vo.CustomerVo;
import com.sunlights.customer.vo.JudjeTokenVo;
import models.Customer;
import models.CustomerSession;
import play.Logger;
import play.cache.Cache;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

/**
 * Created by Administrator on 2014/9/4.
 */
@Transactional
public class CustomerController extends Controller {
  private Form<CustomerFormVo> customerForm = Form.form(CustomerFormVo.class);

  private LoginService loginService = new LoginServiceImpl();

  private CustomerService customerService = new CustomerService();

  private VerifyCodeService verifyCodeService = new VerifyCodeService();

    protected MessageUtil messageUtil = MessageUtil.getInstance();

  /**
   * 查询手机号是否已注册
   * 请求URL： /customer/getusermstr
   * 请求方法： POST
   * <pre>
   * 输入参数：mobilePhoneNo、deviceNo
   * 输出参数：
   * 有数据：{"message":{"severity":0,"code":"0000","summary":"操作成功","detail":"操作成功","fileds":{}},"value":{"userName":null,"nickName":null,"mobilePhoneNo":"15811111171","mobileDisplayNo":"1581****1171","email":null,"gestureOpened":"0","certify":"0","idCardNo":null,"bankCardCount":"0","tradePwdFlag":"0"}}
   * 无数据：
   * {"message":{"severity":0,"code":"2100","summary":"该手机号未注册","detail":"","fileds":{}},"value":null}
   * severity	code	summary	detail
   * 0	0100	操作成功
   * 2	2100	该手机号未注册
   * </pre>
   *
   * @return
   */
  public Result getCustomerByMobilePhoneNo() {
    CustomerFormVo customerFormVo = customerForm.bindFromRequest().get();

    String mobilePhoneNo = customerFormVo.getMobilePhoneNo();
    String deviceNo = customerFormVo.getDeviceNo();

    CustomerVo customerVo = customerService.getCustomerVoByPhoneNo(mobilePhoneNo, deviceNo);
    Message message = new Message(MsgCode.OPERATE_SUCCESS);
    if (customerVo == null) {
      message = new Message(MsgCode.PHONE_NUMBER_NOT_REGISTRY);
    }

    MessageVo<CustomerVo> messageVo = new MessageVo<>(message);
    messageVo.setValue(customerVo);
    return Controller.ok(messageVo.toJson());
  }


  /**
   * <pre>
   * 请求URL： /customer/login
   * 请求方法： POST
   * 输入参数：mobilePhoneNo、passWord、deviceNo(设备号)
   *
   * 输出参数：
   * 成功：{"message":{"severity":0,"code":"0101","summary":"登录成功","detail":"登录成功","fileds":{}},"value":{"userName":null,"nickName":null,"mobilePhoneNo":"15811111175","mobileDisplayNo":"1581****1175","email":null,"gestureOpened":"0","certify":"0","idCardNo":null,"bankCardCount":"0","tradePwdFlag":"0"}}
   * 失败：{"message":{"severity":2,"code":"2107","summary":"密码错误","detail":"剩余次数4","fileds":{}},"value":null}
   * ---------------------------------------------------------------
   * <b>severity	code	                summary	detail</b>
   * 0101	    登录成功
   * 2001	    访问失败	            参数为空
   * 2002	    登录超时	            请重新登录
   * 2100	    该手机号未注册
   * 2106	    密码错误次数过多	    约xx分后再试
   * 2107	    密码错误	            剩余次数xx
   * ---------------------------------------------------------------
   * </pre>
   *
   * @return
   */
  public Result login() {
    Logger.info("==========login====================");
    CustomerFormVo customerFormVo = customerForm.bindFromRequest().get();
    String mobilePhoneNo = customerFormVo.getMobilePhoneNo();
    String deviceNo = customerFormVo.getDeviceNo();

    final Http.Request request = Controller.request();
    Http.Cookie cookie = request.cookie(AppConst.TOKEN);
    String token = cookie == null ? null : cookie.value();

    CustomerSession customerSession = loginService.login(customerFormVo, token, request.remoteAddress());
    if (customerSession != null) {
      Message message = new Message(MsgCode.LOGIN_SUCCESS);
      CustomerVo customerVo = customerService.getCustomerVoByPhoneNo(mobilePhoneNo, deviceNo);
      MessageUtil.getInstance().setMessage(message, customerVo);
      customerService.sessionLoginSessionId(Controller.session(), Controller.response(), customerSession);
    }
    JsonNode json = MessageUtil.getInstance().toJson();
    Logger.info("==========login返回：" + json.toString());
    return Controller.ok(json);
  }

  /**
   * 忘记密码验证码校对
   *
   * @return
   */
  public Result resetpwdCertify() {
    Logger.info("==============resetpwdCertify开始==========");
    CustomerFormVo customerFormVo = customerForm.bindFromRequest().get();
    if (loginService.resetpwdCertify(customerFormVo)) {
      MessageUtil.getInstance().setMessage(new Message(MsgCode.OPERATE_SUCCESS));
    }
    Logger.info("==============resetpwdCertify结束==========");
    return Controller.ok(MessageUtil.getInstance().toJson());
  }

  /**
   * 重置密码
   *
   * @return
   */
  public Result resetpwd() {
    Logger.info("================resetpwd==============");
    CustomerFormVo customerFormVo = customerForm.bindFromRequest().get();

    String mobilePhoneNo = customerFormVo.getMobilePhoneNo();
    String deviceNo = customerFormVo.getDeviceNo();
    String passWord = customerFormVo.getPassWord();

    Customer customer = loginService.resetpwd(mobilePhoneNo, passWord, deviceNo);

    Http.Cookie cookie = Controller.request().cookie(AppConst.TOKEN);
    String token = cookie == null ? null : cookie.value();
    CustomerSession userSession = customerService.getCustomerSession(token);
    if (userSession == null) {// 若为未登录操作重置密码，则自动登录
      Logger.info("===============重置密码之后自动登录===========");
      // 自动登录
      loginService.saveLoginHistory(customer, deviceNo);
      userSession = customerService.createCustomerSession(customer, Controller.request().remoteAddress());
    }
    customerService.sessionLoginSessionId(Controller.session(), Controller.response(), userSession);

    JsonNode json = MessageUtil.getInstance().toJson();
    Logger.info("================resetpwd返回信息：" + json.toString());
    return Controller.ok(json);
  }

  /**
   * 退出
   *
   * @return
   */
  public Result logout() {
    Logger.info("==========logout====================");

    Http.Cookie cookie = Controller.request().cookie(AppConst.TOKEN);
    String token = cookie == null ? null : cookie.value();
    CustomerFormVo customerFormVo = customerForm.bindFromRequest().get();
    String mobilePhoneNo = customerFormVo.getMobilePhoneNo();
    String deviceNo = customerFormVo.getDeviceNo();
    loginService.logout(mobilePhoneNo, deviceNo, token);

    MessageVo messageVo = new MessageVo(new Message(MsgCode.LOGOUT_SUCCESS));
    return Controller.ok(messageVo.toJson());
  }

  /**
   * 手势登录
   *
   * @return
   */
  public Result loginByges() {
    Logger.info("==========loginByGesture====================");
    CustomerFormVo customerFormVo = customerForm.bindFromRequest().get();
    String mobilePhoneNo = customerFormVo.getMobilePhoneNo();
    String deviceNo = customerFormVo.getDeviceNo();

    Http.Cookie cookie = Controller.request().cookie(AppConst.TOKEN);
    String token = cookie == null ? null : cookie.value();

    CustomerSession customerSession = loginService.loginByGesture(customerFormVo, token, Controller.request().remoteAddress());

    if (customerSession != null) {
      Message message = new Message(MsgCode.LOGIN_SUCCESS);
      CustomerVo customerVo = customerService.getCustomerVoByPhoneNo(mobilePhoneNo, deviceNo);
      MessageUtil.getInstance().setMessage(message, customerVo);
      customerService.sessionLoginSessionId(Controller.session(), Controller.response(), customerSession);
    }

    JsonNode json = MessageUtil.getInstance().toJson();
    Logger.info("==========loginByges返回：" + json.toString());

    return Controller.ok(json);
  }

  /**
   * 密码确认
   *
   * @return
   */
  public Result confirmPwd() {
    CustomerFormVo customerFormVo = customerForm.bindFromRequest().get();
    String mobilePhoneNo = customerFormVo.getMobilePhoneNo();
    String passWord = customerFormVo.getPassWord();
    loginService.confirmPwd(mobilePhoneNo, passWord);


    Message message = new Message(MsgCode.OPERATE_SUCCESS);
    return Controller.ok(new MessageVo(message).toJson());
  }

  /**
   * 保存手势密码
   *
   * @return
   */
  public Result saveGesturePwd() {
    Logger.info("========saveGesturePwd================");
    customerService.validateCustomerSession(Controller.request(), Controller.session(), Controller.response());
    CustomerFormVo vo = customerForm.bindFromRequest().get();
    CustomerVo customerVo = loginService.saveGesturePwd(vo);
    MessageVo<CustomerVo> messageVo = new MessageVo<>(new Message(MsgCode.GESTURE_PASSWORD_SUCCESS));
    messageVo.setValue(customerVo);
    return Controller.ok(messageVo.toJson());
  }

  public Result getToken() {
      CustomerFormVo customerFormVo = customerForm.bindFromRequest().get();
      String token = customerFormVo.getToken();
      Object obj = Cache.get(token);
      JudjeTokenVo judjeTokenVo = new JudjeTokenVo();
      if(obj == null) {
          judjeTokenVo.setToken(false);
      } else {
          judjeTokenVo.setToken(true);
      }
      messageUtil.setMessage(new Message(Severity.INFO, MsgCode.OPERATE_SUCCESS), judjeTokenVo);
      Controller.response().setHeader("Access-Control-Allow-Origin","*");
      JsonNode jsonNode = messageUtil.toJson();
      Logger.debug("jsonNode = " + jsonNode);
      return ok(jsonNode);
  }

}
