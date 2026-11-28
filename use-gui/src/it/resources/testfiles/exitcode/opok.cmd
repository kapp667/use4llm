!create c : C
!set c.n := 0
!openter c inc()
!set c.n := 1
!opexit
info opstack
