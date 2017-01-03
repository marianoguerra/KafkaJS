function onMessage(key, value) {
	var obj = JSON.parse(value);
	return JSON.stringify(obj + 1);
}