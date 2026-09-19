import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { OperatorAdminTools } from './OperatorAdminTools'

afterEach(cleanup)

it('requires exact account confirmation and a reason before revoking an operator', async () => {
  const request = vi.fn().mockResolvedValue(undefined)
  render(<OperatorAdminTools request={request} reason="Review operator access" openTarget={vi.fn()} />)
  await userEvent.click(screen.getByRole('button', { name: 'Operator roles' }))
  fireEvent.change(screen.getByLabelText('Account reference'), { target: { value: 'a51b7468-bdb1-4dc7-b0b1-4351bc5f1ed1' } })
  await userEvent.selectOptions(screen.getByLabelText('Platform role'), 'REVOKED')
  await userEvent.type(screen.getByLabelText('Role change reason'), 'Operator has left the team')
  const save = screen.getByRole('button', { name: 'Save operator role' })
  expect(save).toBeDisabled()
  fireEvent.change(screen.getByLabelText(/Confirm account reference/), { target: { value: 'a51b7468-bdb1-4dc7-b0b1-4351bc5f1ed1' } })
  await userEvent.click(save)
  expect(request).toHaveBeenCalledWith('/operators/a51b7468-bdb1-4dc7-b0b1-4351bc5f1ed1', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ role: null, reason: 'Operator has left the team', confirmation: 'a51b7468-bdb1-4dc7-b0b1-4351bc5f1ed1' }) }))
  expect(await screen.findByRole('status')).toHaveTextContent('Operator role saved')
})

it('requires the selected restriction reference to reverse a pending appeal', async () => {
  const request=vi.fn().mockResolvedValue([{id:'appeal',restrictionId:'restriction',reportId:'report',userId:'user',body:'Please review this decision.',status:'PENDING',resolution:null,createdAt:'2026-09-18T00:00:00Z'}])
  render(<OperatorAdminTools request={request} reason="Review appeal" openTarget={vi.fn()} />)
  await userEvent.click(screen.getByRole('button',{name:'Review appeals'}))
  await userEvent.click(screen.getByRole('button',{name:'Load appeals'}))
  await userEvent.click(await screen.findByRole('button',{name:'Review appeal'}))
  await userEvent.selectOptions(screen.getByLabelText('Decision'),'REVERSED')
  await userEvent.type(screen.getByLabelText('Decision reason sent to the account'),'The evidence does not support this restriction.')
  expect(screen.getByRole('button',{name:'Save appeal decision'})).toBeDisabled()
  await userEvent.type(screen.getByLabelText(/Confirm restriction reference/),'restriction')
  await userEvent.click(screen.getByRole('button',{name:'Save appeal decision'}))
  expect(request).toHaveBeenLastCalledWith('/appeals/appeal/resolution',expect.objectContaining({method:'POST',body:expect.stringContaining('REVERSED')}))
})
