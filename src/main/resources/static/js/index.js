$(function () {
	$("#publishBtn").click(publish);
});

function publish() {
	$("#publishModal").modal("hide");

	// 获取标题和内容
	let title = $("#recipient-name").val();
	let content = $("#message-text").val();
	// 发送异步请求(POST)
	$.post(
		CONTEXT_PATH + "/discuss/addDiscussPost",
		{"title":title,"content":content},
		function (data) {
			// 将json字符串转换为json对象
			data = $.parseJSON(data);
			// 显示提示框信息
			$("#hintBody").text(data.msg);
			$("#hintModal").modal("show");
			// 2秒后自动隐藏提示框
			setTimeout(function(){
				$("#hintModal").modal("hide");
				// 刷新页面
				if(data.code == 0){ // 判断是否是成功的
					window.location.reload();
				}
			}, 2000);
		}
	)
}