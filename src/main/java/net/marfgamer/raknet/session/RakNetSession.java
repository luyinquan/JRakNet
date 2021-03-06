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
package net.marfgamer.raknet.session;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import net.marfgamer.raknet.RakNet;
import net.marfgamer.raknet.exception.RakNetException;
import net.marfgamer.raknet.exception.packet.PacketQueueOverloadException;
import net.marfgamer.raknet.exception.packet.RecursiveSplitException;
import net.marfgamer.raknet.exception.packet.SplitPacketQueueException;
import net.marfgamer.raknet.exception.packet.UnexpectedPacketException;
import net.marfgamer.raknet.protocol.Message;
import net.marfgamer.raknet.protocol.Reliability;
import net.marfgamer.raknet.protocol.identifier.MessageIdentifiers;
import net.marfgamer.raknet.protocol.raknet.internal.Acknowledge;
import net.marfgamer.raknet.protocol.raknet.internal.CustomPacket;
import net.marfgamer.raknet.protocol.raknet.internal.EncapsulatedPacket;

/**
 * Represents a session in RakNet, used by the internal handlers to easily track
 * data and send packets which normally require much more data to send
 *
 * @author Trent Summerlin
 */
public abstract class RakNetSession implements RakNet, MessageIdentifiers, Reliability.INTERFACE {

	// Channel data
	private final Channel channel;
	private final InetSocketAddress address;

	// Session data
	private long sessionId = -1;
	private short maximumTransferUnit = MINIMUM_TRANSFER_UNIT;
	private long latency = -1;

	// Packet sequencing data
	private int sendSeqNumber;
	private int receiveSeqNumber;
	private long lastSendTime;
	private long lastReceiveTime;
	private int receivedPacketsThisSecond;

	// Queue data
	private int splitId;
	private int sendMessageIndex;
	private int[] sendIndex;
	private int[] receiveIndex;
	private final ArrayList<Integer> receivedCustoms;
	private final ConcurrentHashMap<Integer, CustomPacket> reliableQueue;
	private final ConcurrentHashMap<Integer, CustomPacket> recoveryQueue;
	private final ConcurrentHashMap<Integer, HashMap<Integer, EncapsulatedPacket>> splitQueue;

	public RakNetSession(Channel channel, InetSocketAddress address) {
		this.channel = channel;
		this.address = address;
		this.sendIndex = new int[32];
		this.receiveIndex = new int[32];
		this.receivedCustoms = new ArrayList<Integer>();
		this.reliableQueue = new ConcurrentHashMap<Integer, CustomPacket>();
		this.recoveryQueue = new ConcurrentHashMap<Integer, CustomPacket>();
		this.splitQueue = new ConcurrentHashMap<Integer, HashMap<Integer, EncapsulatedPacket>>();
	}

	/**
	 * Returns the session's remote address
	 * 
	 * @return InetAddress
	 */
	public InetAddress getAddress() {
		return address.getAddress();
	}

	/**
	 * Returns the session's remote port
	 * 
	 * @return int
	 */
	public int getPort() {
		return address.getPort();
	}

	/**
	 * Returns the session's remote address as a <code>InetSocketAddress</code>
	 * 
	 * @return InetSocketAddress
	 */
	public InetSocketAddress getSocketAddress() {
		return this.address;
	}

	/**
	 * Returns the sessions's ID
	 * 
	 * @return long
	 */
	public long getSessionId() {
		return this.sessionId;
	}

	/**
	 * Sets the session's ID
	 * 
	 * @param sessionId
	 */
	public void setSessionId(long sessionId) {
		this.sessionId = sessionId;
	}

	/**
	 * Returns the session's MTU size
	 * 
	 * @return short
	 */
	public short getMaximumTransferUnit() {
		return this.maximumTransferUnit;
	}

	/**
	 * Sets the session's MTU size
	 * 
	 * @param maximumTransferUnit
	 */
	public void setMaximumTransferUnit(short maximumTransferUnit) {
		this.maximumTransferUnit = maximumTransferUnit;
	}

	/**
	 * Returns the session's latency
	 * 
	 * @return long
	 */
	public long getLatency() {
		return this.latency;
	}

	/**
	 * Sets the session's latency
	 * 
	 * @param latency
	 */
	public void setLatency(long latency) {
		this.latency = latency;
	}

	/**
	 * Returns the amount of packets that have been received this second
	 * 
	 * @return int
	 */
	public int getReceivedPacketsThisSecond() {
		return this.receivedPacketsThisSecond;
	}

	/**
	 * Updates the amount of packets received from the session
	 */
	public void pushReceivedPacketsThisSecond() {
		this.receivedPacketsThisSecond++;
	}

	/**
	 * Resets the amount of packets received this second from the session
	 */
	public void resetReceivedPacketsThisSecond() {
		this.receivedPacketsThisSecond = 0;
	}

	/**
	 * Returns the last time a packet was sent to the session
	 * 
	 * @return long
	 */
	public long getLastSendTime() {
		return this.lastSendTime;
	}

	/**
	 * Returns the last time a packet was received from the session
	 * 
	 * @return long
	 */
	public long getLastReceiveTime() {
		return this.lastReceiveTime;
	}

	/**
	 * Updates the last time a packet was received from the session
	 */
	public void pushLastReceiveTime(long amount) {
		this.lastReceiveTime += amount;
	}

	/**
	 * Resets the last time the packet was received from the session
	 */
	public void resetLastReceiveTime() {
		this.lastReceiveTime = 0L;
	}

	/**
	 * Sends an <code>EncapsulatedPacket</code> wrapped in a
	 * <code>CustomPacket</code>.
	 * 
	 * @param encapsulated
	 */
	public final void sendEncapsulated(EncapsulatedPacket encapsulated) {
		try {
			this.sendEncapsulated(encapsulated, false);
		} catch (RecursiveSplitException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sends an <code>EncapsulatedPacket</code> wrapped in a
	 * <code>CustomPacket</code>.
	 * 
	 * @param encapsulated
	 * @param recursive
	 * @throws RecursiveSplitException
	 */
	private final void sendEncapsulated(EncapsulatedPacket encapsulated, boolean recursive)
			throws RecursiveSplitException {
		// If packet is too big, split it up
		if (CustomPacket.HEADER_LENGTH + EncapsulatedPacket.getHeaderLength(encapsulated.reliability, false)
				+ encapsulated.payload.length > this.maximumTransferUnit) {
			if (!recursive) {
				EncapsulatedPacket[] splitEncapsulated = EncapsulatedPacket.split(encapsulated, maximumTransferUnit,
						splitId++);
				for (EncapsulatedPacket split : splitEncapsulated) {
					this.sendEncapsulated(split, true);
				}
			} else {
				throw new RecursiveSplitException(this);
			}
		} else {
			// Update session data
			if (encapsulated.reliability.isReliable()) {
				encapsulated.messageIndex = sendMessageIndex++;
			} else {
				encapsulated.messageIndex = 0;
			}
			if (encapsulated.reliability.isOrdered() || encapsulated.reliability.isSequenced()) {
				encapsulated.orderIndex = this.sendIndex[encapsulated.orderChannel]++;
			} else {
				encapsulated.orderChannel = 0;
				encapsulated.orderIndex = 0;
			}

			// Send CustomPacket
			CustomPacket custom = new CustomPacket();
			custom.seqNumber = this.sendSeqNumber++;
			custom.packets.add(encapsulated);
			custom.encode();
			this.sendRaw(custom);
			if (encapsulated.reliability.isReliable()) {
				reliableQueue.put(custom.seqNumber, custom);
			}
			recoveryQueue.put(custom.seqNumber, custom);
		}
	}

	/**
	 * Sends an EncapsulatedPacket using the specified packet and reliability
	 * 
	 * @param packet
	 * @param reliability
	 */
	public final void sendPacket(Reliability reliability, Message packet) {
		EncapsulatedPacket encapsulated = new EncapsulatedPacket();
		encapsulated.reliability = reliability;
		encapsulated.payload = packet.array();
		this.sendEncapsulated(encapsulated);
	}

	/**
	 * Sends raw data to the session
	 * 
	 * @param packet
	 */
	public final void sendRaw(Message packet) {
		channel.writeAndFlush(new DatagramPacket(packet.buffer(), address));
		this.lastSendTime = System.currentTimeMillis();
	}

	/**
	 * Returns all reliable packets that have not yet been Acknowledged
	 * 
	 * @return CustomPacket[]
	 */
	public CustomPacket[] getReliableQueue() {
		return this.reliableQueue.values().toArray(new CustomPacket[reliableQueue.size()]);
	}

	/**
	 * Returns all packets that have not yet been Acknowledged
	 * 
	 * @return CustomPacket[]
	 */
	public final CustomPacket[] getRecoveryQueue() {
		return this.recoveryQueue.values().toArray(new CustomPacket[recoveryQueue.size()]);
	}

	/**
	 * Removes as many unreliable packets as possible until the recovery queue
	 * size is smaller than the maximum amount of packets in a queue or there
	 * are no more unreliable packets
	 */
	public final void cleanRecoveryQueue() {
		for (CustomPacket custom : recoveryQueue.values()) {
			if (custom.packets.size() > 0) {
				// Remove CustomPacket based on reliability
				EncapsulatedPacket encapsulated = custom.packets.get(0);
				if (!encapsulated.reliability.isReliable() && recoveryQueue.size() > MAX_PACKETS_PER_QUEUE) {
					recoveryQueue.remove(custom.seqNumber);
				}

				// Buffer is no longer overflowing
				if (recoveryQueue.size() <= MAX_PACKETS_PER_QUEUE) {
					break;
				}
			} else {
				// Glitched recovery packet!
				recoveryQueue.remove(custom.seqNumber);
			}
		}
	}

	/**
	 * Removes all packets in the ACK packet from the recovery queue, as they
	 * have already been acknowledged
	 * 
	 * @param ack
	 * @throws UnexpectedPacketException
	 */
	public final void handleAck(Acknowledge ack) throws UnexpectedPacketException {
		if (ack.getId() == ID_ACK) {
			for (int packet : ack.packets) {
				reliableQueue.remove(packet);
				recoveryQueue.remove(packet);
			}
		} else {
			throw new UnexpectedPacketException(this, ID_ACK, ack.getId());
		}
	}

	/**
	 * Resends all packets with the ID's contained in the NACK packet
	 * 
	 * @param nack
	 * @throws UnexpectedPacketException
	 */
	public final void handleNack(Acknowledge nack) throws UnexpectedPacketException {
		if (nack.getId() == ID_NACK) {
			int[] packets = nack.packets;
			for (int i = 0; i < packets.length; i++) {
				CustomPacket recovered = recoveryQueue.get(packets[i]);
				if (recovered != null) {
					this.sendRaw(recovered);
				}
			}
		} else {
			throw new UnexpectedPacketException(this, ID_NACK, nack.getId());
		}
	}

	public final void handleCustom0(CustomPacket custom) throws RakNetException {
		// Acknowledge packet even if it has been received before
		Acknowledge ack = new Acknowledge(ID_ACK);
		ack.packets = new int[] { custom.seqNumber };
		ack.encode();
		this.sendRaw(ack);

		// Make sure this packet wasn't already received
		if (!receivedCustoms.contains(custom.seqNumber)) {
			// Make sure none of the packets were lost
			if (custom.seqNumber - receiveSeqNumber > 1) {
				Acknowledge nack = new Acknowledge(ID_NACK);
				int[] missing = new int[custom.seqNumber - receiveSeqNumber - 1];
				for (int i = 0; i < missing.length; i++) {
					missing[i] = receiveSeqNumber + i + 1;
				}
				nack.packets = missing;
				nack.encode();
				this.sendRaw(nack);
			}
			this.receiveSeqNumber = custom.seqNumber;

			// Handle encapsulated packets
			for (EncapsulatedPacket encapsulated : custom.packets) {
				this.handleEncapsulated0(encapsulated);
			}
			receivedCustoms.add(custom.seqNumber);
		}
	}

	private final void handleEncapsulated0(EncapsulatedPacket encapsulated) throws RakNetException {
		// Handle packet order based on it's reliability
		Reliability reliability = encapsulated.reliability;

		if (reliability.isOrdered()) {
			// TODO: Ordered packets
		} else if (reliability.isSequenced()) {
			if (encapsulated.orderIndex < receiveIndex[encapsulated.orderChannel]) {
				return; // Packet is old, no error needed
			}
			receiveIndex[encapsulated.orderChannel] = encapsulated.orderIndex + 1;
		}

		if (encapsulated.split == true) {
			// Check if split packet exists
			if (!splitQueue.containsKey(encapsulated.splitId)) {
				// Check queues
				if (splitQueue.size() > MAX_SPLITS_PER_QUEUE) {
					throw new PacketQueueOverloadException(this, "split queue", MAX_SPLITS_PER_QUEUE);
				}
				if (encapsulated.splitCount > MAX_SPLIT_COUNT) {
					throw new SplitPacketQueueException(this, encapsulated);
				}

				// Create split packet
				HashMap<Integer, EncapsulatedPacket> split = new HashMap<Integer, EncapsulatedPacket>();
				split.put(encapsulated.splitIndex, encapsulated);
				splitQueue.put(encapsulated.splitId, split);
			} else {
				// Update split packet
				HashMap<Integer, EncapsulatedPacket> split = splitQueue.get(encapsulated.splitId);
				split.put(encapsulated.splitIndex, encapsulated);
				splitQueue.put(encapsulated.splitId, split);
			}

			// Check if split packet is complete
			if (splitQueue.get(encapsulated.splitId).size() == encapsulated.splitCount) {
				// Write raw data to buffer
				ByteBuf b = Unpooled.buffer();
				int size = 0;
				HashMap<Integer, EncapsulatedPacket> packets = splitQueue.get(encapsulated.splitId);
				for (int i = 0; i < encapsulated.splitCount; i++) {
					b.writeBytes(packets.get(i).payload);
					size += packets.get(i).payload.length;
				}
				byte[] data = Arrays.copyOfRange(b.array(), 0, size);
				splitQueue.remove(encapsulated.splitId);

				// Create EncapsulatedPacket and handle it
				EncapsulatedPacket ep = new EncapsulatedPacket();
				ep.payload = data;
				ep.orderChannel = encapsulated.orderChannel;
				ep.reliability = encapsulated.reliability;
				this.handleEncapsulated0(ep);
			}
			return;
		}

		// Handle packet
		this.handleEncapsulated(encapsulated);
	}

	public abstract void handleEncapsulated(EncapsulatedPacket encapsulated);

}
