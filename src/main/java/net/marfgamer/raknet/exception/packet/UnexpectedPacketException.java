/*
 *       _   _____            _      _   _          _   
 *      | | |  __ \          | |    | \ | |        | |  
 *      | | | |__) |   __ _  | | __ |  \| |   ___  | |_ 
 *  _   | | |  _  /   / _` | | |/ / | . ` |  / _ \ | __|
 * | |__| | | | \ \  | (_| | |   <  | |\  | |  __/ | |_ 
 *  \____/  |_|  \_\  \__,_| |_|\_\ |_| \_|  \___|  \__|
 *                                                  
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Trent Summerlin

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.  
 */
package net.marfgamer.raknet.exception.packet;

import net.marfgamer.raknet.exception.RakNetException;
import net.marfgamer.raknet.protocol.MessageIdentifiers;

/**
 * Thrown when a handler is expecting a packet and receives something else
 * instead
 *
 * @author Trent Summerlin
 */
public class UnexpectedPacketException extends RakNetException {

	private static final long serialVersionUID = -3793043367215871424L;

	private final int requiredId;

	public UnexpectedPacketException(int requiredId, int retrievedId) {
		super("Packet must be " + MessageIdentifiers.getPacketName(requiredId) + " but instead got a "
				+ (MessageIdentifiers.getPacketName(retrievedId) != null ? MessageIdentifiers.getPacketName(retrievedId)
						: "unknown packet")
				+ "!");
		this.requiredId = requiredId;
	}

	public UnexpectedPacketException(int requiredId) {
		super("Packet must be 0x" + Integer.toHexString(requiredId).toUpperCase() + "!");
		this.requiredId = requiredId;
	}

	public int getRequiredId() {
		return this.requiredId;
	}

	public String getRequiredString() {
		return ("0x" + Integer.toHexString(requiredId).toUpperCase());
	}

	@Override
	public String getLocalizedMessage() {
		return "Packet ID must be " + Integer.toHexString(requiredId).toUpperCase() + "!";
	}

}