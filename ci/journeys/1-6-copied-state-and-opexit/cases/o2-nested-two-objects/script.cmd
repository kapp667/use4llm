!create t1 : T
!create t2 : T
!openter t1 name()
!openter t2 name()
!opexit
info opstack
!opexit 'x'
info opstack
!opexit 'y'
info opstack
