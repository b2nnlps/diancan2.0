/*
API公共库
*/
var username,hash,addUrl,newMess=false,firstOpen=0;
var orders=[];

username=getQueryString("username");
hash=getQueryString("hash");
addUrl="username="+username+"&hash="+hash;


function getOrder(type,status) {//获取订单信息 type 0厨房 1传菜员
    // 获取信息
    $.ajax({
        url: 'http://ms.n39.cn/food/api/get-order?status='+status+"&"+addUrl,
		dataType: 'jsonp',
		data: '',
		jsonp:'callback', 
        timeout: 5000,
        success: function (res) {
		   res=res.data;
		 //  console.log(res);
		    
			if(type==0)
			  cpdl(res,status);
		   if(type==1)
			  ccdl(res,status);
		  
        },
        error: function () {
            console.log('加载失败');
        },
        complete: function (XMLHttpRequest, status) { //请求完成后最终执行参数
            if (status == 'timeout' || status == 'error') {//超时,status还有success,error等值的情况
                console.log(status);
            }
            if (status == 'success') {
            }
        }
    });
}
function ccdl(res,status){
	var i,text="",len=res.length;
	for(i=0;i<len;i++){
		if(orders.indexOf(res[i].id)==-1){
			orders.push(res[i].id);
			newMess=true;
			text+="<ul>";
			text+="<h4>桌号："+res[i].table+"桌</h4>";
			text+="<div><span>"+res[i].food_name+"</span>X"+res[i].num+"</div>";
			text+="<li>"+res[i].created_time+"</li>";
			text+="<p>"+res[i].text+"</p>";
			text+="<div class=\"pot_box\">";
			if(status==1)
				text+="<input type=\"checkbox\" id=\"checkbox_d"+res[i].id+"\" class=\"chk_4\" onclick=\"chuanCai("+res[i].id+")\"/><label for=\"checkbox_d"+res[i].id+"\"></label>";
			//if(status==2)
				//text+="<input type=\"checkbox\" id=\"checkbox_y"+i+"\" class=\"chk_4\" checked/><label for=\"checkbox_y"+i+"\"></label>";
			text+="</div>";
			text+="</ul>";
		}
		if( res[i].status==2){//刷新菜品状态,如果订单状态已更新，但还存在视图，则删除
			o=$("#dcc").find('#checkbox_d'+res[i].id).html();
			if(o!=undefined){
				setTimeout("hideChuanCaiOrder("+res[i].id+")",2000);//更新移动该元素
			}
		}
	}
	if(newMess) {
		if(status==1)
			$("#dcc").append(text);
		if(status==2)
				$("#ycc").append(text);
			console.log("新消息"); 
			if(firstOpen>1)
				playSound();
		}else console.log("无消息");
		firstOpen++;
		newMess=false;
}
function cpdl(res,status){
	var i,text="",len=res.length;
	var o;
	for(i=0;i<len;i++){
		if(orders.indexOf(res[i].id)==-1){
			orders.push(res[i].id);
			newMess=true;
			text="<ul>";
			text+="<div class=\"clearfix\"><em>"+res[i].food_name+"</em><span>X"+res[i].num+"</span></div>";
			text+="<li>桌号"+res[i].table+"</li>";
			text+="<li>"+res[i].created_time+"</li>";
			text+="<p>"+res[i].text+"</p>";
			text+="<div class=\"pot_box\">";
			if(status==0)
				text+="<input type=\"checkbox\" id=\"checkbox_d"+res[i].id+"\" class=\"chk_4\" onclick=\"chuGuo("+res[i].id+")\" /><label for=\"checkbox_d"+res[i].id+"\"></label>";
			//if(status==1)
			//text+="<input type=\"checkbox\" id=\"checkbox_y"+i+"\" class=\"chk_4\" checked=\"checked\" readonly=\"readonly\"/><label for=\"checkbox_y"+i+"\"></label>";
			text+="</div>";
			text+="</ul>";
			if(status==0)
				$("#wcg").append(text);
			if(status==1)
				$("#ycg").append(text);	
		}
		if( res[i].status==1){//刷新菜品状态,如果订单状态已更新，但还存在视图，则删除
			o=$("#wcg").find('#checkbox_d'+res[i].id).html();
			if(o!=undefined){
				setTimeout("hideChuGuoOrder("+res[i].id+")",2000);//更新移动该元素
			}
		}
	}
	
	if(newMess) {
		if(firstOpen>1)
			playSound();
		console.log("新消息"); 
		}else console.log("无消息");
		firstOpen++;
		newMess=false;
}

function chuGuo(id){
	var name=$("#checkbox_d"+id).parent().parent().find("em").html();
	layer.confirm('<'+name+'>', {
		title:'出锅确认',
		btn: ['出锅','否'] //按钮
}, function(){
    layer.msg('提交成功', {icon: 1});
	$("#checkbox_d"+id).attr('disabled','disabled');
	checkOrder(id,1);
}, function(){
	$("#checkbox_d"+id).attr('checked',false);
});
}

function hideChuGuoOrder(id){
	var parent=$("#checkbox_d"+id).parent().parent();
	$(parent).slideUp(800,function(){
		$(parent).find("label").remove();
		var html="<ul>"+$(parent).html()+"</ul>"+$("#ycg").html();//转到隔壁
		$("#ycg").html(html);
		$(this).remove();
	})
}

function chuanCai(id){
	var name=$("#checkbox_d"+id).parent().parent().find("span").html();
	layer.confirm('<'+name+'>', {
		title:'传菜确认',
		btn: ['传菜','否'] //按钮
}, function(){
  layer.msg('提交成功', {icon: 1});
	$("#checkbox_d"+id).attr('disabled','disabled');
	checkOrder(id,2);
}, function(){
	$("#checkbox_d"+id).attr('checked',false);
});
}

function hideChuanCaiOrder(id){
	var parent=$("#checkbox_d"+id).parent().parent();
	$(parent).slideUp(800,function(){
		$(parent).find("label").remove();
		var html="<ul>"+$(parent).html()+"</ul>"+$("#ycc").html();//转到隔壁
		$("#ycc").html(html);
		$(this).remove();
	})
}

function checkOrder(info_id,status){//出锅、传菜更新
var index = layer.load(0, {shade: false});
	$.ajax({
        url: 'http://ms.n39.cn/food/api/check-order?info_id='+info_id+'&status='+status+"&"+addUrl,
		dataType: 'jsonp',
		data: '',
		jsonp:'callback', 
        timeout: 5000,
        success: function (res) {
		   res=res.data;
		   console.log(res);
		   if(status==1)
			setTimeout("hideChuGuoOrder("+info_id+")",2000);//提交后删除该元素
		   if(status==2)
			setTimeout("hideChuanCaiOrder("+info_id+")",2000);//提交后删除该元素
			index = layer.load(0, {shade: false,time:1});
        },
        error: function () {
            console.log('加载失败');
        },
        complete: function (XMLHttpRequest, status) { //请求完成后最终执行参数
            if (status == 'timeout' || status == 'error') {//超时,status还有success,error等值的情况
                console.log(status);
            }
            if (status == 'success') {
            }
        }
    });
}

function playSound()//播放提示音
    {
      var borswer = window.navigator.userAgent.toLowerCase();
      if ( borswer.indexOf( "ie" ) >= 0 )
      {
        //IE内核浏览器
        var strEmbed = '<embed name="embedPlay" src="raw/dingdong.wav" autostart="true" hidden="true" loop="false"></embed>';
        if ( $( "body" ).find( "embed" ).length <= 0 )
          $( "body" ).append( strEmbed );
        var embed = document.embedPlay;

        //浏览器不支持 audion，则使用 embed 播放
        embed.volume = 100;
        //embed.play();这个不需要
      } else
      {
        //非IE内核浏览器
        var strAudio = "<audio id='audioPlay' src='raw/dingdong.wav' hidden='true'>";
        if ( $( "body" ).find( "audio" ).length <= 0 )
          $( "body" ).append( strAudio );
        var audio = document.getElementById( "audioPlay" );

        //浏览器支持 audion
        audio.play();
      }
    }

function getQueryString(name) { //获取get传过来的用户信息
var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i"); 
var r = window.location.search.substr(1).match(reg); 
if (r != null) return unescape(r[2]); return null; 
} 