function init(config) {
	return JSON.parse(config);
}

function onMessage(key, value, state) {
	var obj = JSON.parse(value);
	return JSON.stringify(obj + 1);
}