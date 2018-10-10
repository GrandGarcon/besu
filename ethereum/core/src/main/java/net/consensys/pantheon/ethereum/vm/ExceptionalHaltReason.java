package net.consensys.pantheon.ethereum.vm;

public enum ExceptionalHaltReason {
  NONE,
  INSUFFICIENT_GAS,
  INSUFFICIENT_STACK_ITEMS,
  INVALID_JUMP_DESTINATION,
  INVALID_OPERATION,
  INVALID_RETURN_DATA_BUFFER_ACCESS,
  TOO_MANY_STACK_ITEMS,
  ILLEGAL_STATE_CHANGE
}
