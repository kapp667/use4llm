!create c : C
!set c.n := 0
!openter c op()
!set c.n := 5
!opexit
info opstack
!opexit
