/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNode.AttachInteropTypeNode;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.AttachInteropTypeNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMTruffleWriteManagedToGlobal extends LLVMIntrinsic {

    @Child AttachInteropTypeNode attachType = AttachInteropTypeNodeGen.create();

    @Specialization
    protected LLVMManagedPointer doGlobal(LLVMGlobal address, LLVMManagedPointer value,
                    @Cached("create()") LLVMGlobal.GetFrame getFrameNode,
                    @Cached("getContextReference()") ContextReference<LLVMContext> context) {
        LLVMManagedPointer typedValue = LLVMManagedPointer.cast(attachType.execute(value, address.getInteropType()));
        MaterializedFrame globalFrame = getFrameNode.execute(context.get());
        globalFrame.setObject(address.getSlot(), typedValue);
        return typedValue;
    }

    // this is a workaround because @Fallback does not support @Cached
    @TruffleBoundary
    @Specialization(guards = "isOther(address)")
    protected Object doOther(Object address, Object value,
                    @Cached("create()") LLVMGlobal.GetFrame getFrameNode,
                    @Cached("getContextReference()") ContextReference<LLVMContext> context) {
        // TODO: (timfel) This is so slow :(
        MaterializedFrame globalFrame = getFrameNode.execute(context.get());
        for (FrameSlot slot : globalFrame.getFrameDescriptor().getSlots()) {
            if (slot.getKind() == FrameSlotKind.Object) {
                try {
                    if (globalFrame.getObject(slot) == address) {
                        globalFrame.setObject(slot, value);
                        return value;
                    }
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }
        }
        return address;
    }

    protected static boolean isOther(Object address) {
        return !(address instanceof LLVMGlobal);
    }
}
