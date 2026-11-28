!create c : C
!set c.n := 0
!openter c inc()
!set c.n := 5
!opexit
info opstack
!opexit
