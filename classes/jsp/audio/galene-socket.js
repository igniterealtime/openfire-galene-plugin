class GaleneSocket
{
    constructor(connection) {
		this.OPEN = connection.connected;
		this.connection = connection;	
		this.readyState = this.OPEN;
		
		this.connection.addHandler((iq) => {
			const json_ele = iq.querySelector("s2c");
			console.debug('GaleneSocket handler', json_ele.innerHTML);				
			
			if (this.onmessage) this.onmessage({data: json_ele.innerHTML});			
			return true;
			
		}, "urn:xmpp:sfu:galene:0", 'iq');	
		
		setTimeout(() => {
			console.debug('GaleneSocket start');			
			if (this.onopen) this.onopen();				
		});

		console.debug('GaleneSocket constructor', this);			
	}
	
	close(code, reason) {
		console.debug('GaleneSocket close', code, reason);		
		if (this.onclose) this.onclose({code, reason});
	}
	
	send(text) {
		console.debug('GaleneSocket send', text);			
		this.connection.sendIQ($iq({type: 'set', to: this.connection.domain}).c('c2s', {xmlns: 'urn:xmpp:sfu:galene:0'}).t(text), (res) => {
			//console.debug('GaleneSocket send response', res);	
		});			
	}
	
}